package tun

import (
	"syscall"
	"testing"
)

func TestCloseIfOpen(t *testing.T) {
	var st syscall.Stat_t

	other, err := syscall.Open("/dev/null", syscall.O_RDWR, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer syscall.Close(other)

	CloseIfOpen(other)
	if syscall.Fstat(other, &st) != nil {
		t.Fatal("закрыт чужой файл")
	}

	tun, err := syscall.Open("/dev/net/tun", syscall.O_RDWR, 0)
	if err != nil {
		t.Skip("нет /dev/net/tun:", err)
	}

	CloseIfOpen(tun)
	if syscall.Fstat(tun, &st) == nil {
		syscall.Close(tun)
		t.Fatal("туннель остался открытым")
	}

	// Уже закрытый номер — ничего не делаем и не падаем.
	CloseIfOpen(tun)
}
