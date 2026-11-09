package config

import (
	"bytes"
	"compress/gzip"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	U "net/url"
	"os"
	"path/filepath"
	"strconv"
	"testing"
)

func TestFetchHTTPDownloadLimits(t *testing.T) {
	for _, subscription := range []bool{true, false} {
		limit := providerDownloadLimit
		kind := "provider"
		if subscription {
			limit = profileDownloadLimit
			kind = "profile"
		}
		for _, mode := range []string{"length", "chunked", "no-length", "gzip"} {
			for _, extra := range []int64{0, 1} {
				t.Run(fmt.Sprintf("%s/%s/%d", kind, mode, extra), func(t *testing.T) {
					size := limit + extra
					server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
						body := &downloadTestReader{left: size}
						if mode == "no-length" {
							conn, rw, err := w.(http.Hijacker).Hijack()
							if err != nil {
								t.Error(err)
								return
							}
							defer conn.Close()
							_, _ = rw.WriteString("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
							_ = rw.Flush()
							_, _ = io.Copy(conn, body)
							return
						}
						if mode == "gzip" {
							var compressed bytes.Buffer
							gz := gzip.NewWriter(&compressed)
							_, _ = io.Copy(gz, body)
							_ = gz.Close()
							w.Header().Set("Content-Encoding", "gzip")
							w.Header().Set("Content-Length", strconv.Itoa(compressed.Len()))
							_, _ = w.Write(compressed.Bytes())
							return
						}
						if mode == "length" {
							w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
						} else {
							w.WriteHeader(200)
							w.(http.Flusher).Flush()
						}
						_, _ = io.Copy(w, body)
					}))
					defer server.Close()
					file := filepath.Join(t.TempDir(), "config.yaml")
					_ = os.WriteFile(file, []byte("previous"), 0600)
					u, _ := U.Parse(server.URL)
					_, err := fetch(u, file, subscription, nil)
					if extra == 1 {
						if err == nil {
							t.Fatal("overflow reported success")
						}
						assertDownloadFiles(t, file, "previous")
					} else {
						if err != nil {
							t.Fatal(err)
						}
						info, _ := os.Stat(file)
						if info.Size() != limit {
							t.Fatalf("read %d want %d", info.Size(), limit)
						}
					}
				})
			}
		}
	}
}

func TestFetchBrokenHTTPPreservesFile(t *testing.T) {
	for _, mode := range []string{"length", "chunked", "gzip-checksum"} {
		t.Run(mode, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if mode == "gzip-checksum" {
					var b bytes.Buffer
					gz := gzip.NewWriter(&b)
					_, _ = gz.Write([]byte("synthetic body"))
					_ = gz.Close()
					data := b.Bytes()
					data[len(data)-8] ^= 0xff
					w.Header().Set("Content-Encoding", "gzip")
					_, _ = w.Write(data)
					return
				}
				conn, rw, err := w.(http.Hijacker).Hijack()
				if err != nil {
					t.Error(err)
					return
				}
				defer conn.Close()
				if mode == "length" {
					_, _ = rw.WriteString("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nshort")
				} else {
					_, _ = rw.WriteString("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nshort\r\n")
				}
				_ = rw.Flush()
			}))
			defer server.Close()
			file := filepath.Join(t.TempDir(), "config.yaml")
			_ = os.WriteFile(file, []byte("previous"), 0600)
			u, _ := U.Parse(server.URL)
			if _, err := fetch(u, file, true, nil); err == nil {
				t.Fatal("broken response accepted")
			}
			assertDownloadFiles(t, file, "previous")
		})
	}
}
