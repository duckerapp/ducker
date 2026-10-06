package com.github.kr328.clash.service.clash.module

import com.github.kr328.clash.service.store.UserRulesStore
import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.keepGlobalOffDirect
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

/**
 * Загрузка конфига в ядро — одна за раз: для туннеля и для замера узлов без туннеля (D-63).
 *
 * Замер защищался от туннеля только проверкой «туннель запущен», а его запрос мог
 * проскочить за мгновение до создания службы туннеля. Тогда туннель снимал с ядра правки
 * замера (DNS ядра и порты выключены), замер тут же накладывал их снова, и туннель грузил
 * конфиг уже с ними — без DNS ядра. Под замком проверка и загрузка идут подряд (D-110).
 */
internal val configLoadLock = Mutex()

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
    data class LoadException(val message: String)

    private val store = ServiceStore(service)
    private val reload = Channel<Unit>(Channel.CONFLATED)

    /** Конфиг туннеля уже в ядре: по нему служба решает, перемерять ли узлы (D-110). */
    @Volatile
    var ready = false
        private set

    /** Зовётся после каждой удачной загрузки, уже после [ready] (D-110). */
    var onLoaded: (() -> Unit)? = null

    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_PROFILE_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
        }

        var loaded: UUID? = null

        reload.trySend(Unit)

        while (true) {
            val changed: UUID? = select {
                broadcasts.onReceive {
                    if (it.action == Intents.ACTION_PROFILE_CHANGED)
                        UUID.fromString(it.getStringExtra(Intents.EXTRA_UUID))
                    else
                        null
                }
                reload.onReceive {
                    null
                }
            }

            try {
                val current = store.activeProfile
                    ?: throw NullPointerException(Failure.NO_PROFILE)

                if (current == loaded && changed != null && changed != loaded)
                    continue

                // Режим, выбранный кнопкой на «Прокси», — выбор для этой подписки: при
                // обновлении он остаётся, а у другой подписки действует её `mode` (D-143).
                val switched = current != loaded

                loaded = current

                val active = ImportedDao().queryByUUID(current)
                    ?: throw NullPointerException(Failure.NO_PROFILE)

                // Сведения провайдера лежат файлом в каталоге профиля, а не в базе (D-32).
                val provider = ProviderInfo.read(service.importedDir.resolve(active.uuid.toString()))

                // `ducker-local-proxy: off` — провайдер просит туннель без входов локального
                // прокси (D-218): порты и `listeners` закрываем оверрайдом, файл конфига не трогаем.
                // ponytail: `tunnels`, `ss-config`, `vmess-config` и `tuic-server` остаются как в
                // конфиге — в подписках не встречались; понадобятся — такими же полями оверрайда.
                val port = if (provider.localProxyOff) 0 else null

                configLoadLock.withLock {
                    // Замер узлов при выключенном туннеле глушит порты и DNS сессионным
                    // оверрайдом (D-63). Туннелю они нужны, поэтому снимаем — и только эти
                    // поля: в том же слоте живёт оверрайд веб-панели.
                    Clash.queryOverride(Clash.OverrideSlot.Session).let { override ->
                        override.mixedPort = port
                        override.httpPort = port
                        override.socksPort = port
                        override.redirectPort = port
                        override.tproxyPort = port
                        override.listeners = if (provider.localProxyOff) emptyList() else null
                        override.allowLan = null
                        override.dns.enable = null
                        if (switched) override.mode = null

                        Clash.patchOverride(Clash.OverrideSlot.Session, override)
                    }

                    Clash.setAgeSecretKey(active.ageSecretKey?.takeIf { it.isNotBlank() })
                    Clash.setUserRules(UserRulesStore(service).raw(active.uuid))

                    try {
                        Clash.load(service.importedDir.resolve(active.uuid.toString())).await()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e

                        // Первая загрузка не удалась — ядру не с чем работать, служба остановится.
                        // Метка — чтобы главная назвала причиной конфиг, а не туннель (D-227).
                        if (!ready) throw Exception(Failure.LOAD + e.message)

                        // Повторная (обновилась подписка, поменяли настройку или своё правило):
                        // ядро не применяет конфиг, пока он не разобрался, и работает на прежнем
                        // (`config/load.go`, `Load`). Гасить из-за этого VPN — значит молча пустить
                        // трафик мимо него (аудит 2.9, D-207). Ошибку видно на карточке подписки.
                        Log.w("Profile ${active.name} reload failed, the previous config stays: ${e.message}")

                        service.sendProfileUpdateFailed(current, Failure.RELOAD + e.message)

                        continue
                    }

                    val remove = SelectionDao().querySelections(active.uuid)
                        .filterNot { Clash.patchSelector(it.proxy, it.selected) }
                        .map { it.proxy }

                    SelectionDao().removeSelections(active.uuid, remove)

                    keepGlobalOffDirect(active.uuid)
                }

                ready = true

                onLoaded?.invoke()

                // В шторке и на плитке должно стоять имя провайдера, а не имя подписки:
                // подписка обычно называется ссылкой, по которой её добавили.
                StatusProvider.currentProfile = provider.brandName
                    .ifBlank { provider.profileTitle }
                    .ifBlank { active.name }

                service.sendProfileLoaded(current)

                Log.d("Profile ${active.name} loaded")
            } catch (e: Exception) {
                return enqueueEvent(LoadException(e.message ?: "Unknown"))
            }
        }
    }
}
