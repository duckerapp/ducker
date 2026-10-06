package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    /**
     * Публиковать HTTP-прокси в свойствах VPN-сети. Им пользуются приложения, которые
     * уважают системный прокси, — в основном браузеры; остальные идут через tun.
     *
     * По умолчанию выключено: путь через tun одинаков для всех приложений и виден
     * в правилах, а прокси — вторая, параллельная дорога с другим поведением (D-61).
     */
    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = false
    )

    /**
     * `VpnService.allowBypass()` — разрешает приложению осознанно уйти мимо туннеля,
     * привязав сокет к нижней сети. Обычный трафик так не утекает: нужно, чтобы
     * приложение само этого захотело.
     *
     * ducker ставит false, в отличие от CMFA: человек включает VPN, чтобы трафик шёл
     * через VPN, а политику «кто идёт напрямую» задаёт конфиг провайдера
     * (`tun.exclude-package`) — это видно и проверяемо. Тихая лазейка мимо этой политики
     * противоречит тому, что человек видит на экране (D-61).
     */
    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = false
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    /**
     * Сетевой стек TUN. Пусто — «как задано в профиле» (D-86).
     *
     * Умолчание именно пустое, а не `system`: пока человек не выбрал стек сам, решает
     * конфиг провайдера, как и было раньше. Выбрал — его выбор старше конфига, иначе
     * настройка в приложении была бы декоративной: и наш шаблон, и шаблоны панелей
     * почти всегда задают `stack` сами.
     */
    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = ""
    )

    /**
     * Когда поднялся туннель, в миллисекундах. Ноль — не поднят.
     *
     * Хранится в общем хранилище, а не в памяти интерфейса: иначе после перезапуска
     * приложения время работы считалось бы заново, хотя туннель как стоял, так и стоит.
     */
    var tunnelStartedAt by store.long(
        key = "tunnel_started_at",
        defaultValue = 0L,
    )

    /** Когда туннель последний раз поднимали сами после его гибели (D-90). */
    var tunnelRestoredAt by store.long(
        key = "tunnel_restored_at",
        defaultValue = 0L,
    )

    /**
     * Туннель поднят в режиме «Блокировать соединения без VPN», и часть приложений идёт
     * мимо туннеля. Такие приложения система в этом режиме оставляет без сети совсем
     * (F-110), и главная об этом предупреждает. Пишет служба при подъёме туннеля.
     */
    var lockdownOffline by store.boolean(
        key = "lockdown_offline",
        defaultValue = false,
    )

    /**
     * Почему VPN остановился не по команде — сырой текст для `Failure` (D-227). Пусто — причины
     * нет: туннель поднялся или карточку на главной закрыли. Пишет `StopNotice`.
     */
    var stopReason by store.string(
        key = "stop_reason",
        defaultValue = "",
    )

    /** О каком сроке какой подписки уже предупредили — `ExpiryNotice`. */
    var expiryNotice by store.string(
        key = "expiry_notice",
        defaultValue = "",
    )

    /** Когда включили «Подробный журнал», в миллисекундах; ноль — выключен (`VerboseLog`). */
    var verboseLogSince by store.long(
        key = "verbose_log_since",
        defaultValue = 0L,
    )

    /**
     * Скорость в уведомлении. Ключ новый (D-215): под прежним `dynamic_notification`
     * умолчанием было «включено», а выключено должно стать у всех, и у уже установивших.
     */
    var dynamicNotification by store.boolean(
        key = "speed_notification",
        defaultValue = false
    )

    /**
     * Отправлять ли панели идентификатор устройства и сведения о нём (D-36).
     *
     * По умолчанию включено: для панели с включённым лимитом устройств без него
     * приходит не профиль, а отказ (FACTS.md, F-34). Выключатель нужен тем, у кого
     * провайдер устройства не считает.
     */
    var sendHwid by store.boolean(
        key = "send_hwid",
        defaultValue = true
    )
}