package config

import (
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type downloadTestReader struct {
	left, read        int64
	step              int
	readErr, closeErr error
	closed            bool
}

func (r *downloadTestReader) Read(p []byte) (int, error) {
	if r.left == 0 {
		if r.readErr != nil {
			return 0, r.readErr
		}
		return 0, io.EOF
	}
	if r.step > 0 && len(p) > r.step {
		p = p[:r.step]
	}
	if int64(len(p)) > r.left {
		p = p[:r.left]
	}
	for i := range p {
		p[i] = 'x'
	}
	r.left -= int64(len(p))
	r.read += int64(len(p))
	return len(p), nil
}
func (r *downloadTestReader) Close() error { r.closed = true; return r.closeErr }

func assertDownloadFiles(t *testing.T, file string, want string) {
	t.Helper()
	data, err := os.ReadFile(file)
	if err != nil || string(data) != want {
		t.Fatalf("previous file lost: bytes=%d err=%v", len(data), err)
	}
	entries, err := os.ReadDir(filepath.Dir(file))
	if err != nil || len(entries) != 1 {
		t.Fatalf("temporary files remain: %v, %v", entries, err)
	}
}

func TestDownloadBoundaries(t *testing.T) {
	for _, subscription := range []bool{true, false} {
		limit := providerDownloadLimit
		kind := "provider"
		if subscription {
			limit = profileDownloadLimit
			kind = "profile"
		}
		for _, extra := range []int64{0, 1} {
			name := kind + "/exact"
			if extra == 1 {
				name = kind + "/overflow"
			}
			t.Run(name, func(t *testing.T) {
				file := filepath.Join(t.TempDir(), "config.yaml")
				if err := os.WriteFile(file, []byte("previous"), 0600); err != nil {
					t.Fatal(err)
				}
				r := &downloadTestReader{left: limit + extra, step: 997}
				err := writeDownload(file, r, subscription)
				if !r.closed {
					t.Fatal("reader not closed")
				}
				if extra == 1 {
					if err == nil {
						t.Fatal("overflow reported success")
					}
					assertDownloadFiles(t, file, "previous")
				} else {
					if err != nil {
						t.Fatal(err)
					}
					info, err := os.Stat(file)
					if err != nil || info.Size() != limit {
						t.Fatalf("exact size: %v %v", info, err)
					}
					if info.Mode().Perm() != 0600 {
						t.Fatal("download is not private")
					}
				}
			})
		}
	}
}

func TestDownloadReadAndCloseErrorsPreserveFile(t *testing.T) {
	for _, mode := range []string{"read", "close", "error-at-exact-limit"} {
		t.Run(mode, func(t *testing.T) {
			file := filepath.Join(t.TempDir(), "config.yaml")
			_ = os.WriteFile(file, []byte("previous"), 0600)
			broken := errors.New("synthetic read failure")
			r := &downloadTestReader{left: 31, step: 3, readErr: broken}
			if mode == "close" {
				r.readErr = nil
				r.closeErr = broken
			}
			if mode == "error-at-exact-limit" {
				r.left = profileDownloadLimit
				r.step = 0
			}
			err := writeDownload(file, r, true)
			if !errors.Is(err, broken) {
				t.Fatalf("error lost: %v", err)
			}
			if !r.closed {
				t.Fatal("reader not closed")
			}
			assertDownloadFiles(t, file, "previous")
		})
	}
}

func TestDownloadStopsAfterDiagnosticByte(t *testing.T) {
	file := filepath.Join(t.TempDir(), "config.yaml")
	r := &downloadTestReader{left: profileDownloadLimit * 2}
	if err := writeDownload(file, r, true); err == nil {
		t.Fatal("oversized stream accepted")
	}
	if r.read != profileDownloadLimit+1 || !r.closed {
		t.Fatalf("read %d closed %v", r.read, r.closed)
	}
	entries, _ := os.ReadDir(filepath.Dir(file))
	if len(entries) != 0 {
		t.Fatal("failed new download left data")
	}
}

func TestDownloadRenameFailureCleansTemp(t *testing.T) {
	file := filepath.Join(t.TempDir(), "occupied")
	_ = os.Mkdir(file, 0700)
	_ = os.WriteFile(filepath.Join(file, "previous"), []byte("old"), 0600)
	if err := writeDownload(file, io.NopCloser(strings.NewReader("new")), true); err == nil {
		t.Fatal("rename error hidden")
	}
	entries, _ := os.ReadDir(filepath.Dir(file))
	if len(entries) != 1 {
		t.Fatal("temporary data remains")
	}
	data, _ := os.ReadFile(filepath.Join(file, "previous"))
	if string(data) != "old" {
		t.Fatal("destination changed")
	}
}
