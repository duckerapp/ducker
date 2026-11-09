package config

import "testing"

// Списка хостингов нет (D-192): подписка на любом домене по https получает данные устройства.
func TestAnyHTTPSHostReceivesDeviceData(t *testing.T) {
	for _, address := range []string{"https://provider.invalid/sub", "HTTPS://PROVIDER.invalid./sub", "https://raw.githubusercontent.com/sub", "https://test.pages.dev/sub", "https://user@provider.invalid/sub"} {
		if !deviceHeadersAllowed(address) {
			t.Errorf("provider denied: %s", address)
		}
	}
}

func TestDeviceHeadersRequireHTTPS(t *testing.T) {
	if err := SetRequestHeaders(`{"User-Agent":"mihomo/1.19.31 ducker/QA","X-HWID":"SYNTHETIC-HWID-1234","x-device-model":"QA MODEL","x-device-os":"QA OS","x-ver-os":"QA VERSION","X-Hwid-Extra":"QA EXTRA"}`); err != nil {
		t.Fatal(err)
	}
	defer SetRequestHeaders("")
	for _, address := range []string{"http://provider.invalid/sub", "content://files/sub", "https:///sub", "https://"} {
		h := buildRequestHeader(address, "mihomo/fallback ducker")
		for name := range h {
			if sensitiveSubscriptionHeader(name) {
				t.Errorf("%s: device header %s", address, name)
			}
		}
		if h.Get("User-Agent") != "mihomo/1.19.31 ducker/QA" {
			t.Fatal("panel User-Agent lost")
		}
	}
	h := buildRequestHeader("https://provider.invalid/sub", "mihomo/fallback ducker")
	if h.Get("x-hwid") != "SYNTHETIC-HWID-1234" || h.Get("x-device-model") != "QA MODEL" {
		t.Fatal("HTTPS provider headers lost")
	}
	// Replacing the map with UA only is Kotlin's existing sendHwid=false path.
	if err := SetRequestHeaders(`{"User-Agent":"mihomo/1.19.31 ducker/QA"}`); err != nil {
		t.Fatal(err)
	}
	h = buildRequestHeader("https://provider.invalid/sub", "mihomo/fallback ducker")
	if h.Get("x-hwid") != "" || h.Get("x-device-model") != "" || h.Get("User-Agent") != "mihomo/1.19.31 ducker/QA" {
		t.Fatal("disabled HWID or panel UA changed")
	}
	if err := SetRequestHeaders(""); err != nil {
		t.Fatal(err)
	}
	h = buildRequestHeader("https://provider.invalid/sub", "mihomo/fallback ducker")
	if h.Get("x-hwid") != "" || h.Get("User-Agent") != "mihomo/fallback ducker" {
		t.Fatal("header reset/fallback changed")
	}
}

// A control character in a value — a phone's model name, say — made the HTTP client refuse the request.
func TestRequestHeadersDropControlCharacters(t *testing.T) {
	if err := SetRequestHeaders(`{"User-Agent":"mihomo/1 ducker/2 (android)","x-device-model":"Q\r\nX-Evil: 1\tModel\u007f ","x-device-os":"\n"}`); err != nil {
		t.Fatal(err)
	}
	defer SetRequestHeaders("")
	h := buildRequestHeader("https://provider.invalid/sub", "fallback")
	if h.Get("x-device-model") != "QX-Evil: 1Model" || h.Get("X-Evil") != "" || len(h.Values("x-device-os")) != 0 {
		t.Fatalf("headers %v", h)
	}
}
