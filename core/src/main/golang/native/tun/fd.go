package tun

import "syscall"

// /dev/tun — символьное устройство 10:200 (TUN_MINOR в ядре Linux).
const tunDevice = 10<<8 | 200

// CloseIfOpen закрывает дескриптор туннеля, если он ещё открыт. sing_tun закрывает его сам,
// только когда успел создать интерфейс; при более раннем отказе дескриптор оставался открытым,
// и Android держал VPN включённым без туннеля (D-226). Номер мог уже достаться другому файлу,
// поэтому сверяем, что за ним по-прежнему туннель.
func CloseIfOpen(fd int) {
	var st syscall.Stat_t

	if syscall.Fstat(fd, &st) == nil && st.Mode&syscall.S_IFMT == syscall.S_IFCHR && uint64(st.Rdev) == tunDevice {
		_ = syscall.Close(fd)
	}
}
