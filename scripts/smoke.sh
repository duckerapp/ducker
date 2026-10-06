#!/usr/bin/env bash
# Дымовой тест ducker.
#
# Собирает релизную сборку, ставит на подключённый телефон и проверяет главное:
# приложение запускается, туннель поднимается, трафик идёт через узел, правила
# маршрутизации работают. Нужен после обновления ядра (docs/CORE-UPDATE.md),
# перед раздачей сборки и после любой правки, которая трогает ядро.
#
#   ./scripts/smoke.sh              собрать и проверить
#   ./scripts/smoke.sh --no-build   проверить уже собранное
#
# Данные приложения не трогаются: сборка подписана тем же ключом и встаёт поверх.
#
# Туннель тест включает и выключает командами START_CLASH и STOP_CLASH. Приложение
# слушается их, только когда в его настройках включено «Управление из других
# приложений» (D-192). После свежей установки оно выключено, и снаружи через adb
# его не включить — только рукой на экране настроек.
#
# Имена переменных латиницей не для красоты: bash не умеет кириллицу в именах,
# но прекрасно печатает её в тексте.
set -u

cd "$(dirname "$0")/.."

ABI=${ABI:-arm64-v8a}
# Номер версии в имени файла меняется с каждым релизом, поэтому берём самый свежий
# подходящий, а не прибитый гвоздями.
APK=${APK:-$(ls -t app/build/outputs/apk/meta/release/ducker-*-meta-"$ABI"-release.apk 2>/dev/null | head -1)}
PKG=app.ducker.android
EXTERNAL_HINT='в настройках ducker включи «Управление из других приложений»'
FAILED=0
STEP=0

step() { STEP=$((STEP+1)); printf '\n%2d. %s\n' "$STEP" "$1"; }
ok()   { printf '    ✓ %s\n' "$1"; }
bad()  { printf '    ✗ %s\n' "$1"; FAILED=$((FAILED+1)); }

phone() { adb shell "$1" 2>/dev/null | tr -d '\r'; }

# Поднят ли VPN, спрашиваем у системы, а не ищем порт прокси ядра: в подписке на
# встроенном шаблоне порта нет (D-216). Android 15 и 16 пишут «VPN CONNECTED extra: VPN:<пакет>»,
# Android 12 (проверено на MIUI 14) — «VPN CONNECTED extra: » без пакета, но с «OwnerUid: <uid>» в той
# же строке, старые (проверено на 10) — «type: VPN[], state: CONNECTED» без имени пакета.
tunnel_up() {
  local uid; uid=$(phone "cmd package list packages -U $PKG" | sed -n 's/.*uid://p')
  phone "dumpsys connectivity" | grep -qE "VPN CONNECTED.*($PKG|OwnerUid: ${uid:-none}([^0-9]|\$))|type: VPN\[\], state: CONNECTED/"
}

wait_tunnel() {
  for _ in $(seq 1 "$1"); do
    tunnel_up && return 0
    adb shell sleep 1 >/dev/null 2>&1
  done
  return 1
}

adb get-state >/dev/null 2>&1 || { echo "Телефон не подключён. adb connect <адрес> или воткни кабель."; exit 1; }

step "Собираем релизную сборку"
if [ "${1:-}" = "--no-build" ]; then
  ok "пропущено по просьбе"
else
  if ./gradlew :app:assembleMetaRelease -q >/tmp/ducker-smoke-build.log 2>&1; then
    ok "собралось"
  else
    bad "сборка упала, смотри /tmp/ducker-smoke-build.log"
    tail -20 /tmp/ducker-smoke-build.log
    exit 1
  fi
fi
[ -f "$APK" ] || { bad "нет файла $APK"; exit 1; }

step "Проверяем, каким ключом подписано"
SIGNER=$(find "$HOME/Android/Sdk/build-tools" -name apksigner 2>/dev/null | sort | tail -1)
if [ -n "$SIGNER" ]; then
  DN=$("$SIGNER" verify --print-certs "$APK" 2>/dev/null | grep -m1 'certificate DN')
  case "$DN" in
    *"CN=ducker"*) ok "наш ключ ($DN)" ;;
    *"Android Debug"*) bad "подписано ОТЛАДОЧНЫМ ключом — нет signing.properties" ;;
    *) bad "непонятная подпись: $DN" ;;
  esac
else
  ok "apksigner не найден, проверку пропустили"
fi

step "Смотрим, какое ядро внутри"
SO="core/build/outputs/golang/metaRelease/$ABI/libclash.so"
SUBMODULE=$(git -C core/src/foss/golang/clash log --oneline -1 2>/dev/null)
ok "подмодуль: ${SUBMODULE:-неизвестно}"
if [ -f "$SO" ] && command -v go >/dev/null; then
  GOVER=$(go version -m "$SO" 2>/dev/null | grep -m1 'github.com/metacubex/mihomo' | awk '{print $3}')
  ok "mihomo ${GOVER:-версия не прочиталась}"
fi

# Версию, которую приложение показывает в «О приложении», считает CMake — один раз
# при настройке. Сменили пин подмодуля, а настройка не перезапустилась — в приложении
# висит версия прошлого ядра (F-102). Ловим это здесь, чтобы не ловить в поддержке.
HEAD_SHORT=$(git -C core/src/foss/golang/clash rev-parse --short HEAD 2>/dev/null)
BAKED=$(grep -m1 'define GIT_VERSION' core/src/main/cpp/version.h 2>/dev/null | awk '{print $3}')
if [ -z "$HEAD_SHORT" ] || [ -z "$BAKED" ]; then
  ok "версию в version.h проверить не удалось"
elif [ "${BAKED#*_}" != "${HEAD_SHORT}_${BAKED##*_}" ]; then
  bad "приложение покажет ядро '$BAKED', а собрано с $HEAD_SHORT — выполни: touch core/src/main/cpp/CMakeLists.txt && ./gradlew :app:assembleMetaRelease"
else
  ok "в приложении покажется '$BAKED' — совпадает с подмодулем"
fi

step "Запоминаем, был ли туннель поднят"
WAS_UP=no
tunnel_up && WAS_UP=yes
ok "был поднят: $WAS_UP"

# Падения ищем только с этого момента: в журнале падений лежат и старые записи.
SINCE=$(phone "date '+%m-%d %H:%M:%S.000'")

step "Ставим на телефон"
if adb install -r -d "$APK" 2>&1 | grep -q Success; then
  ok "установилось поверх, данные на месте"
else
  bad "установка не прошла"
  exit 1
fi

step "Запускаем приложение"
# Вторая попытка — не поблажка: 23.09.2026 первый запуск сразу после установки поверх
# однажды не вернул TotalTime, а вручную через секунду всё запустилось. Настоящая поломка
# запуска повторится и на второй попытке.
for ATTEMPT in 1 2; do
  STARTMS=$(adb shell "am start -W -n $PKG/com.github.kr328.clash.MainActivityAlias" 2>/dev/null | tr -d '\r' | grep '^TotalTime' | awk '{print $2}')
  [ -n "$STARTMS" ] && break
  adb shell sleep 3 >/dev/null 2>&1
done
if [ -n "$STARTMS" ]; then
  ok "первый кадр за ${STARTMS} мс"
else
  bad "не запустилось"
fi
adb shell sleep 4 >/dev/null 2>&1

step "Смотрим адрес выхода БЕЗ туннеля"
adb shell "am start -a $PKG.action.STOP_CLASH" >/dev/null 2>&1
adb shell sleep 5 >/dev/null 2>&1
# Туннель на месте — значит, команду не приняли. Дальше идти нельзя: «прямой» адрес
# оказался бы адресом узла, и тест решил бы, что трафик идёт мимо.
if tunnel_up; then
  adb shell sleep 5 >/dev/null 2>&1
  if tunnel_up; then
    bad "туннель не опустился по команде: $EXTERNAL_HINT и запусти тест ещё раз"
    exit 1
  fi
fi
DIRECT_IP=$(phone "curl -s --max-time 12 http://ip-api.com/line?fields=query")
if [ -n "$DIRECT_IP" ]; then
  ok "без туннеля выходим через $DIRECT_IP"
else
  bad "адрес не определился — нет сети или на телефоне нет curl"
fi

step "Поднимаем туннель"
adb shell "am start -a $PKG.action.START_CLASH" >/dev/null 2>&1
if wait_tunnel 25; then
  ok "поднялся"
else
  bad "не поднялся за 25 с (нет разрешения на VPN? нет активной подписки? или команду не приняли: $EXTERNAL_HINT)"
fi

step "Проверяем, что трафик идёт через узел"
# Несколько сервисов по очереди: 23.09.2026 ip-api.com перестал отвечать через узел
# (на 0.4.8 так же), и тест падал из-за чужого сайта. Панель провайдера может пускать
# часть таких сервисов напрямую — поэтому засчитывается первый адрес, отличный от
# прямого; все ответили прямым — трафик идёт мимо узла.
VIA_IP=""
SEEN_DIRECT=""
for SERVICE in http://ip-api.com/line?fields=query https://icanhazip.com https://api.ipify.org \
               https://ifconfig.me/ip; do
  IP=$(phone "curl -s --max-time 10 $SERVICE" | tr -d '[:space:]')
  [ -z "$IP" ] && continue
  if [ "$IP" = "$DIRECT_IP" ]; then SEEN_DIRECT=1; continue; fi
  VIA_IP=$IP
  break
done
if [ -n "$VIA_IP" ]; then
  # Страна и провайдер — для глаз, не для проверки: ip-api.com может и не ответить.
  VIA=$(phone "curl -s --max-time 10 http://ip-api.com/line?fields=country,isp" | tr '\n' ' ')
  ok "через узел: $VIA_IP (${SERVICE#*://}${VIA:+, $VIA})"
elif [ -n "$SEEN_DIRECT" ]; then
  bad "все сервисы видят прямой адрес ($DIRECT_IP) — трафик идёт мимо узла"
else
  bad "адрес не определился ни одним сервисом"
fi

step "Проверяем DNS и маршруты"
for site in https://www.youtube.com https://www.google.com https://ya.ru; do
  CODE=$(phone "curl -s --max-time 15 -o /dev/null -w '%{http_code}' $site")
  case "$CODE" in
    2*|3*) ok "$site отвечает $CODE" ;;
    *) bad "$site не ответил (код '$CODE')" ;;
  esac
done

step "Гоняем трафик через узел"
# Несколько источников по очереди: 23.09.2026 proof.ovh.net перестал отвечать через узел
# на любой сборке, и тест падал из-за чужого сайта. Берём первые 10 МБ (-r) — у Hetzner
# файл на 100 МБ.
SPEED=""
for SOURCE in https://nbg1-speed.hetzner.com/100MB.bin http://speedtest.tele2.net/10MB.zip \
              https://proof.ovh.net/files/10Mb.dat; do
  SPEED=$(phone "curl -s --max-time 40 -r 0-10485759 -o /dev/null -w '%{speed_download}' $SOURCE")
  SPEED=${SPEED%%.*}
  if [ -n "$SPEED" ] && [ "$SPEED" -gt 200000 ] 2>/dev/null; then
    break
  fi
done
if [ -n "$SPEED" ] && [ "$SPEED" -gt 200000 ] 2>/dev/null; then
  ok "10 МБ прокачались на $(( SPEED / 1024 )) КБ/с (${SOURCE#*://})"
else
  bad "прокачка не пошла ни с одного источника (последняя скорость '$SPEED' Б/с)"
fi

step "Ищем падения в журнале"
# Только наши процессы, по точному имени: при выпуске 0.4.25 тест покраснел из-за старых
# записей о пакете app.ducker.android.audit. Java-падение — «Process: <имя>,», падение ядра —
# «>>> <имя> <<<» и «Cmdline: <имя>».
CRASH_RE="(Process: |>>> |Cmdline: )${PKG//./\\.}(:[A-Za-z]+)?(,| <<<|\$)"
CRASH_LOG=$(phone "logcat -d -b crash -T '$SINCE'")
CRASHES=$(printf '%s\n' "$CRASH_LOG" | grep -cE "$CRASH_RE")
if [ "${CRASHES:-0}" -eq 0 ] 2>/dev/null; then
  ok "падений нет"
else
  bad "в журнале падений $CRASHES строк про наше приложение"
  printf '%s\n' "$CRASH_LOG" | grep -E -A 5 "$CRASH_RE" | head -20
fi

step "Возвращаем телефон как было"
if [ "$WAS_UP" = "no" ]; then
  adb shell "am start -a $PKG.action.STOP_CLASH" >/dev/null 2>&1
  ok "туннель опущен обратно"
elif tunnel_up; then
  ok "туннель оставлен поднятым"
else
  bad "до теста туннель был поднят, а сейчас опущен — включи VPN в приложении"
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "ИТОГ: всё зелёное, сборку можно раздавать."
else
  echo "ИТОГ: провалено проверок — $FAILED. Раздавать нельзя, разбираться выше."
fi
exit "$FAILED"
