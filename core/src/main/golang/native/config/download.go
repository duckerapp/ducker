package config

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

const profileDownloadLimit int64 = 16 * 1024 * 1024
const providerDownloadLimit int64 = 64 * 1024 * 1024

var errDownloadTooLarge = errors.New("download exceeds size limit")

type downloadError struct {
	kind  string
	limit int64
	cause error
}

func (e *downloadError) Error() string {
	if errors.Is(e.cause, errDownloadTooLarge) {
		return fmt.Sprintf("DOWNLOAD_TOO_LARGE: %s: %d", e.kind, e.limit)
	}
	return fmt.Sprintf("DOWNLOAD_FAILED: %s: %s", e.kind, e.cause)
}
func (e *downloadError) Unwrap() error { return e.cause }

// The reader is the decoded HTTP body (or content/bundle stream), never the
// Content-Length. Own both streams and replace the destination only after EOF,
// successful closes and sync. One extra byte distinguishes exact size from overflow.
func writeDownload(file string, reader io.ReadCloser, subscription bool) (err error) {
	kind, limit := "provider", providerDownloadLimit
	if subscription {
		kind, limit = "profile", profileDownloadLimit
	}
	defer func() {
		if err != nil {
			err = &downloadError{kind, limit, err}
		}
	}()
	closedReader := false
	defer func() {
		if !closedReader {
			_ = reader.Close()
		}
	}()

	dir := filepath.Dir(file)
	if err = os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	f, err := os.CreateTemp(dir, "."+filepath.Base(file)+".download-*")
	if err != nil {
		return err
	}
	temp := f.Name()
	closedFile := false
	defer func() {
		if !closedFile {
			_ = f.Close()
		}
		_ = os.Remove(temp)
	}()

	n, err := io.Copy(f, io.LimitReader(reader, limit+1))
	if err != nil {
		return err
	}
	if n > limit {
		return errDownloadTooLarge
	}
	closedReader = true
	if err = reader.Close(); err != nil {
		return err
	}
	if err = f.Sync(); err != nil {
		return err
	}
	closedFile = true
	if err = f.Close(); err != nil {
		return err
	}
	return os.Rename(temp, file)
}
