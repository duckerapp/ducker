# Безопасность

*English version is below.*

## Как сообщить об уязвимости

**Не открывайте публичный Issue.** Пока ошибка не исправлена, описание в открытом доступе
помогает тем, кто захочет ею воспользоваться.

Напишите закрыто: на странице репозитория **Security → Report a vulnerability**. Сообщение увидит
только владелец проекта.

Что приложить:
- что именно уязвимо и чем это грозит пользователю;
- как повторить: шаги, версия ducker, телефон и версия Android;
- если есть — пример или журнал, **без настоящих ссылок подписок, ключей и адресов узлов**.

## Что сюда относится

- Приложение: интерфейс, служба VPN, обёртка ядра на Go (`core/src/main/golang`), сборка.
- Наши правки ядра mihomo (форк [duckerapp/mihomo](https://github.com/duckerapp/mihomo), ветка `ducker`)
  и форков `utls` и `sing-mux`.

Уязвимость в самом mihomo, которой нет в наших правках, сообщайте авторам ядра —
[MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo). Вопросы о работе конкретного провайдера или
его серверов — к провайдеру: ducker только клиент.

## Какие версии поддерживаются

Исправления выходят в следующей версии. Поддерживается только последний релиз.

## Чего ждать

Проект ведёт один человек, поэтому сроки ответа не гарантированы, но сообщения об уязвимостях
разбираются в первую очередь. Когда исправление выйдет, в описании релиза будет сказано, что было
исправлено. Автора сообщения укажем, если он этого хочет.

---

# Security

## Reporting a vulnerability

**Please do not open a public Issue.** Report privately via **Security → Report a vulnerability**
on the repository page. Include what is affected, how to reproduce it (steps, ducker version, device,
Android version) and, if possible, a log — **without real subscription links, keys or server
addresses**.

## Scope

The app (UI, VPN service, Go wrapper in `core/src/main/golang`, build scripts) and our patches to
mihomo, `utls` and `sing-mux`. Issues in upstream mihomo that are not caused by our patches should go
to [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo).

Only the latest release is supported. This is a one-person project: response times are not
guaranteed, but security reports are handled first.
