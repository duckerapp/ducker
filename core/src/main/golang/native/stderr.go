package main

import (
	"bytes"
	"os"
	P "path"
	"syscall"
)

const stderrLimit = 1024 * 1024

func openStderrLog(home string) (*os.File, error) {
	path := P.Join(home, "stderr.log")
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_APPEND, 0600)
	if err != nil {
		return nil, err
	}
	info, err := f.Stat()
	if err == nil && info.Size() > stderrLimit {
		err = compactStderrLog(path, info.Size())
		_ = f.Close()
		if err != nil {
			return nil, err
		}
		return os.OpenFile(path, os.O_WRONLY|os.O_APPEND, 0600)
	}
	if err != nil {
		_ = f.Close()
		return nil, err
	}
	return f, nil
}

// Keep recent complete panic records before the next core startup. A bare tail
// can start inside a multiline secret, so retain only from a diagnostic header.
// Replace atomically: a failed write must not erase the previous crash.
func compactStderrLog(path string, size int64) error {
	input, err := os.Open(path)
	if err != nil {
		return err
	}
	tail := make([]byte, stderrLimit+1)
	_, err = input.ReadAt(tail, size-stderrLimit-1)
	_ = input.Close()
	if err != nil {
		return err
	}
	// Check the preceding byte: a header exactly at the boundary is complete.
	boundary := tail[0] == '\n'
	tail = tail[1:]
	if !boundary {
		if end := bytes.IndexByte(tail, '\n'); end >= 0 {
			tail = tail[end+1:]
		} else {
			tail = nil
		}
	}
	for len(tail) > 0 {
		if bytes.HasPrefix(tail, []byte("[APP] core panic in ")) ||
			bytes.HasPrefix(tail, []byte("panic: ")) || bytes.HasPrefix(tail, []byte("fatal error: ")) {
			break
		}
		if end := bytes.IndexByte(tail, '\n'); end >= 0 {
			tail = tail[end+1:]
		} else {
			tail = nil
		}
	}
	temp, err := os.CreateTemp(P.Dir(path), "stderr-*.tmp")
	if err != nil {
		return err
	}
	defer os.Remove(temp.Name())
	defer temp.Close()
	if _, err = temp.Write(tail); err != nil {
		return err
	}
	if err = temp.Sync(); err != nil {
		return err
	}
	if err = temp.Close(); err != nil {
		return err
	}
	return os.Rename(temp.Name(), path)
}

// redirectStderr перенаправляет стандартный поток ошибок в файл внутри каталога
// приложения.
//
// Зачем. Go печатает текст паники и фатальной ошибки рантайма в дескриптор 2.
// У приложения Android этот дескриптор ведёт в /dev/null, поэтому падение ядра
// выглядит в logcat как голый `SIGABRT` в libclash.so без единой подсказки:
// мост в logcat (log.go) работает через подписку на логи mihomo и до паники
// рантайма не доходит.
//
// Почему в файл, а не в канал с перекачкой в logcat: после вывода паники
// рантайм убивает процесс немедленно, и горутина-читатель может не успеть
// получить управление. Файл переживает падение гарантированно.
//
// Файл лежит в приватном каталоге приложения и в выгрузку логов попадает только
// после чистки (инвариант 4 в PLAN.md): в панике может оказаться содержимое
// структур с адресами узлов.
func redirectStderr(home string) {
	f, err := openStderrLog(home)
	if err != nil {
		return
	}
	defer f.Close()

	// Dup2 на linux/arm64 нет — там только dup3.
	_ = syscall.Dup3(int(f.Fd()), 2, 0)
}
