package main

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
)

func TestStderrStartupRetainsLastCrash(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "stderr.log")
	crash := "panic: synthetic-last-crash\nframe.go:12\n"
	if err := os.WriteFile(file, []byte(strings.Repeat("old diagnostic\n", stderrLimit/8)+crash), 0600); err != nil {
		t.Fatal(err)
	}
	out, err := openStderrLog(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer out.Close()
	data, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(string(data), crash) {
		t.Fatal("last crash was erased on restart")
	}
	if len(data) > stderrLimit {
		t.Fatal("startup log exceeds size limit")
	}
}

func TestStderrCompactionKeepsHeaderExactlyAtTailBoundary(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "stderr.log")
	header, frame := "panic: synthetic-last-crash\n", "\nframe.go:12\n"
	crash := header + strings.Repeat("x", stderrLimit-len(header)-len(frame)) + frame
	if err := os.WriteFile(file, []byte("old diagnostic\n"+crash), 0600); err != nil {
		t.Fatal(err)
	}
	out, err := openStderrLog(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer out.Close()
	got, err := os.ReadFile(file)
	if err != nil || string(got) != crash {
		t.Fatal("complete header at the retention boundary was dropped")
	}
}

func TestStderrCompactionStartsAtACompleteDiagnostic(t *testing.T) {
	for _, header := range []string{"panic: ", "fatal error: ", "[APP] core panic in "} {
		t.Run(header, func(t *testing.T) {
			dir := t.TempDir()
			file := filepath.Join(dir, "stderr.log")
			last := header + "synthetic-last-crash\nframe.go:12\n"
			data := "password: \"" + strings.Repeat("synthetic-secret\n", stderrLimit/8) + "\"\n" + last
			if err := os.WriteFile(file, []byte(data), 0600); err != nil {
				t.Fatal(err)
			}
			out, err := openStderrLog(dir)
			if err != nil {
				t.Fatal(err)
			}
			out.Close()
			got, err := os.ReadFile(file)
			if err != nil || string(got) != last {
				t.Fatalf("incomplete payload retained or last crash lost: size %d, err %v", len(got), err)
			}
		})
	}
}

func TestStderrCompactionFailurePreservesOriginal(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "stderr.log")
	data := []byte("panic: synthetic-last-crash\n")
	if err := os.WriteFile(file, data, 0600); err != nil {
		t.Fatal(err)
	}
	// Simulate a changed/truncated source after stat: ReadAt must fail before replacement.
	if err := compactStderrLog(file, 2*stderrLimit); err == nil {
		t.Fatal("missing read failure")
	}
	got, err := os.ReadFile(file)
	if err != nil || !bytes.Equal(got, data) {
		t.Fatal("failed compaction erased original")
	}
}

func TestStderrLogSizeOnStartup(t *testing.T) {
	for _, size := range []int{0, stderrLimit - 1, stderrLimit, stderrLimit + 1} {
		t.Run(stringSize(size), func(t *testing.T) {
			dir := t.TempDir()
			file := filepath.Join(dir, "stderr.log")
			if err := os.WriteFile(file, make([]byte, size), 0600); err != nil {
				t.Fatal(err)
			}
			out, err := openStderrLog(dir)
			if err != nil {
				t.Fatal(err)
			}
			if _, err := out.WriteString("tail"); err != nil {
				_ = out.Close()
				t.Fatal(err)
			}
			if err := out.Close(); err != nil {
				t.Fatal(err)
			}
			info, err := os.Stat(file)
			if err != nil {
				t.Fatal(err)
			}
			want := int64(size + 4)
			if size > stderrLimit {
				want = 4
			}
			if info.Size() != want {
				t.Fatalf("size %d, want %d", info.Size(), want)
			}
		})
	}
}

func stringSize(size int) string {
	switch size {
	case 0:
		return "empty"
	case stderrLimit - 1:
		return "below_limit"
	case stderrLimit:
		return "at_limit"
	default:
		return "above_limit"
	}
}

func TestStderrLogCreatedPrivate(t *testing.T) {
	dir := t.TempDir()
	out, err := openStderrLog(dir)
	if err != nil {
		t.Fatal(err)
	}
	if err := out.Close(); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(filepath.Join(dir, "stderr.log"))
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0600 {
		t.Fatalf("permissions: %v", info.Mode())
	}
}

func TestRedirectStderrKeepsDuplicatedDescriptor(t *testing.T) {
	if dir := os.Getenv("DUCKER_STDERR_TEST_DIR"); dir != "" {
		redirectStderr(dir)
		if _, err := syscall.Write(2, []byte("redirected")); err != nil {
			os.Exit(1)
		}
		os.Exit(0)
	}
	dir := t.TempDir()
	cmd := exec.Command(os.Args[0], "-test.run=^TestRedirectStderrKeepsDuplicatedDescriptor$")
	cmd.Env = append(os.Environ(), "DUCKER_STDERR_TEST_DIR="+dir)
	if err := cmd.Run(); err != nil {
		t.Fatal(err)
	}
	got, err := os.ReadFile(filepath.Join(dir, "stderr.log"))
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "redirected" {
		t.Fatalf("stderr: %q", got)
	}
}
