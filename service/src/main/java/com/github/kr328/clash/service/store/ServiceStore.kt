package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.MobileState
import org.json.JSONArray
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(PreferenceProvider.createStoreProvider(context))

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

    /**
     * When the network under the tunnel last became another one, ms since the epoch; 0 — never.
     * Delay checks made before it are results of another network, and «Прокси» makes them again
     * when it is looked at (D-299).
     */
    var networkChangedAt by store.long(
        key = "network_changed_at",
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

    /** Which traffic threshold of which subscription has been told about — `TrafficNotice`. */
    var trafficNotice by store.string(
        key = "traffic_notice",
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

    /** «Раздача по Wi-Fi»: its port, kept so the second device is set up once; zero — not chosen yet (D-421). */
    var sharePort by store.int(
        key = "share_port",
        defaultValue = 0
    )

    /** Its password on a Wi-Fi that is not the phone's own, made once (D-421). */
    var shareSecret by store.string(
        key = "share_secret",
        defaultValue = ""
    )

    /** The «Пароль» switch on that Wi-Fi; off — the port is open to everyone there (D-421). */
    var sharePassword by store.boolean(
        key = "share_password",
        defaultValue = true
    )

    /** «Переключение по сети» on «Мобильная сеть» (D-423, D-424): off by default, off keeps what is set below. */
    var mobileOn by store.boolean(
        key = "mobile_on",
        defaultValue = false
    )

    /** The subscription for the mobile network; `null` — not chosen, and nothing switches. */
    var mobileProfile: UUID? by store.typedString(
        key = "mobile_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    /** Members of its main group to try in the person's order; empty — the subscription's own pick stays. */
    var mobileNodes: List<String>
        get() = mobileNodesJson.let { json ->
            runCatching { JSONArray(json).let { array -> List(array.length()) { array.getString(it) } } }.getOrDefault(emptyList())
        }
        set(value) {
            mobileNodesJson = JSONArray(value).toString()
        }

    private var mobileNodesJson by store.string(
        key = "mobile_nodes",
        defaultValue = "[]"
    )

    /**
     * «Список провайдера» (D-534): the list follows the chosen subscription's preset, brought by each of its updates.
     * An edit of the list turns it off.
     */
    var mobileProvider by store.boolean(
        key = "mobile_provider",
        defaultValue = false
    )

    /** The person saved something on the page once: a provider's preset no longer sets the page up by itself (D-534). */
    var mobileSetUp by store.boolean(
        key = "mobile_set_up",
        defaultValue = false
    )

    /** The network kind switching last acted on or the person last picked on, a [NetworkKind] ordinal. */
    var mobileSettled by store.int(
        key = "mobile_settled",
        defaultValue = 0
    )

    /**
     * What Wi-Fi had when the mobile choice came, to bring back on Wi-Fi: the subscription, its main group and the
     * pick there. `null` — nothing to bring back.
     */
    var mobileBackProfile: UUID? by store.typedString(
        key = "mobile_back_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var mobileBackGroup by store.string(
        key = "mobile_back_group",
        defaultValue = ""
    )

    var mobileBackNode by store.string(
        key = "mobile_back_node",
        defaultValue = ""
    )

    /** What Home and the notification say, a [MobileState.Shown] ordinal; read through [mobileShownNow]. */
    var mobileShown by store.int(
        key = "mobile_shown",
        defaultValue = 0
    )

    /**
     * [mobileShown] while it is still true: the mobile choice is in effect only while its subscription is the active
     * one, and a subscription deleted or picked by hand would otherwise leave Home saying «мобильная сеть».
     */
    val mobileShownNow: MobileState.Shown
        get() = MobileState.Shown.entries.getOrElse(mobileShown) { MobileState.Shown.None }
            .takeIf { mobileOn && activeProfile != null && activeProfile == mobileProfile }
            ?: MobileState.Shown.None
}