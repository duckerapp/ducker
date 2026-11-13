package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.backgroundScope
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.AgeKeys
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.store.AnnouncementStore
import com.github.kr328.clash.service.store.ConfigAppsStore
import com.github.kr328.clash.service.store.ConfigTemplate
import com.github.kr328.clash.service.store.ConfigTemplateStore
import com.github.kr328.clash.service.store.SOURCE_FILE
import com.github.kr328.clash.service.store.localSource
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.SubscriptionHeaders
import com.github.kr328.clash.service.util.SubscriptionMirrors
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.replaceWithCopyOf
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()
    private val prefetchLock = Mutex()
    private val prefetchScope = backgroundScope("ProfileProcessor")

    /**
     * Downloads, in the background, the provider files [uuid] has none of yet. A subscription never loaded here
     * downloaded its rule sets that go through a node inside its first load, while the core dropped every connection
     * for up to 20 s: picked for «Мобильная сеть», on the first switch to it. No [profileLock]: [active] waits on it.
     */
    fun prefetch(context: Context, uuid: UUID) {
        prefetchScope.launch {
            prefetchLock.withLock {
                val dir = context.importedDir.resolve(uuid.toString())
                if (!dir.isDirectory) return@withLock

                when (val left = Clash.fetchMissingProviders(dir.absolutePath)) {
                    0 -> Unit
                    -1 -> Log.w("Prefetch: the config of $uuid does not read")
                    else -> Log.w("Prefetch: $left provider files of $uuid still missing")
                }

                // Deleted while the files came: the downloads made its folder again.
                if (!ImportedDao().exists(uuid)) profileLock.withLock { if (!ImportedDao().exists(uuid)) dir.deleteRecursively() }
            }
        }
    }

    /**
     * Панель отказала из-за идентификатора устройства и прислала вместо профиля заглушку
     * (D-26, F-51). Бросается вместо молчаливого успеха: заглушка проходит проверку ядра,
     * поэтому без этого она заменила бы рабочий профиль, а человек увидел бы клиент,
     * который «просто перестал работать».
     *
     * Ошибка прерывает обработку до копирования каталога, поэтому прежний профиль и
     * прежние сведения провайдера остаются на месте.
     */
    class HwidBlockedException(
        val reason: String,
        val announce: String,
        val supportUrl: String,
    ) : Exception(Failure.HWID + reason)

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(NonCancellable) {
            withProcessingLock(context) {
                val snapshot = profileLock.withLock {
                    val pending =
                        PendingDao().queryByUUID(uuid) ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                AgeKeys.apply()

                val force = snapshot.type != Profile.Type.File
                val template = templateOf(snapshot.type, ConfigTemplateStore(context).read(snapshot.uuid))
                val subscriptionInfo =
                    fetchProfile(context, snapshot.source, force, callback, brokenConfigAllowed(context, snapshot.uuid, snapshot.type), template)

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        val shown = announcements(context, snapshot.uuid)

                        context.importedDir.resolve(snapshot.uuid.toString()).replaceWithCopyOf(context.processingDir)
                        ConfigAppsStore(context).dropIfLocked(snapshot.uuid, context.importedDir.resolve(snapshot.uuid.toString()))
                        AnnouncementStore.record(context, snapshot.uuid, shown.second, shown.first)

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        val new = Imported(
                            snapshot.uuid,
                            snapshot.name,
                            snapshot.type,
                            snapshot.source,
                            snapshot.interval,
                            subscriptionInfo?.subUpload ?: 0,
                            subscriptionInfo?.subDownload ?: 0,
                            subscriptionInfo?.subTotal ?: 0,
                            subscriptionInfo?.subExpire ?: 0,
                            old?.createdAt ?: System.currentTimeMillis(),
                            ageSecretKey = snapshot.ageSecretKey,
                            followProvider = snapshot.followProvider,
                        )
                        if (old != null) {
                            ImportedDao().update(new)
                        } else {
                            ImportedDao().insert(new)

                            // A new subscription with none chosen is chosen here, not by the screen that added it:
                            // that screen's process may be gone by the end of the download, and the subscription
                            // stayed downloaded but unchosen — «Подписка не выбрана» (D-370).
                            val store = ServiceStore(context)

                            if (store.activeProfile?.let { ImportedDao().exists(it) } != true) store.activeProfile = snapshot.uuid
                        }

                        PendingDao().remove(snapshot.uuid)

                        context.pendingDir.resolve(snapshot.uuid.toString()).deleteRecursively()

                        context.sendProfileChanged(snapshot.uuid)
                    }
                }

                moveIfAsked(context, snapshot.uuid)
            }
        }
    }

    /**
     * @param switchTo a template to switch to ("" — the subscription's own config), or null to keep the chosen one.
     * A switch rebuilds from the kept download where there is one ([localSource]); the choice is stored only
     * with the config it built, after the swap.
     */
    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?, switchTo: String? = null) {
        withContext(NonCancellable) {
            withProcessingLock(context) {
                val snapshot = profileLock.withLock {
                    val imported =
                        ImportedDao().queryByUUID(uuid) ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                AgeKeys.apply()

                var tries = emptyList<FetchStatus.Try>()

                val chosen = switchTo ?: ConfigTemplateStore(context).read(snapshot.uuid)
                val local = switchTo != null && localSource(context.processingDir, chosen)?.also {
                    if (it.name == SOURCE_FILE) it.renameTo(context.processingDir.resolve("config.yaml"))
                } != null

                val subscriptionInfo = try {
                    val template = templateOf(snapshot.type, chosen)

                    fetchProfile(context, snapshot.source, !local, callback, brokenConfigAllowed(context, snapshot.uuid, snapshot.type), template) {
                        tries = it
                    }
                } catch (e: HwidBlockedException) {
                    // Профиль остаётся прежним, но отметку об отказе нужно сохранить рядом
                    // с ним: иначе человек видит только то, что «ничего не изменилось»,
                    // а причину — нигде (D-26). Следующее удачное обновление перезапишет
                    // сведения провайдера целиком и отметка сама исчезнет.
                    markHwidBlocked(context, snapshot.uuid, e)
                    recordTries(context, snapshot.uuid, tries, e)

                    throw e
                } catch (e: Exception) {
                    recordTries(context, snapshot.uuid, tries, e)

                    throw e
                }

                profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(snapshot.uuid)
                    if (imported != null) {
                        val shown = announcements(context, snapshot.uuid)

                        ProviderInfo.write(
                            context.processingDir,
                            ProviderInfo.read(context.processingDir).copy(updateEndedAt = System.currentTimeMillis(), updateFailure = ""),
                        )
                        context.importedDir.resolve(snapshot.uuid.toString()).replaceWithCopyOf(context.processingDir)
                        if (switchTo != null) ConfigTemplateStore(context).write(snapshot.uuid, switchTo)
                        ConfigAppsStore(context).dropIfLocked(snapshot.uuid, context.importedDir.resolve(snapshot.uuid.toString()))
                        AnnouncementStore.record(context, snapshot.uuid, shown.second, shown.first)

                        val upload = subscriptionInfo?.subUpload
                        if (upload != null) {
                            ImportedDao().update(
                                imported.copy(
                                    upload = upload,
                                    download = subscriptionInfo.subDownload ?: 0,
                                    total = subscriptionInfo.subTotal ?: 0,
                                    expire = subscriptionInfo.subExpire ?: 0,
                                )
                            )
                        }

                        context.sendProfileChanged(snapshot.uuid)
                    }
                }

                moveIfAsked(context, snapshot.uuid)
            }
        }
    }

    /**
     * The provider moves the subscription (`new-url`, `new-domain`; Q-80). The stored link changes only
     * after the new address has given a subscription that passed the core's check, in a whole download
     * of its own; until then the link stays, and the next update tries again while the provider keeps
     * sending the header. The update that brought the header is already committed from the old link, so
     * a failed move costs nothing. A move onto a link another subscription has is refused.
     */
    private suspend fun moveIfAsked(context: Context, uuid: UUID) {
        val dir = context.importedDir.resolve(uuid.toString())
        val from = ImportedDao().queryByUUID(uuid) ?: return
        val target = ProviderInfo.read(dir).moveTo.takeIf { it.isNotEmpty() && it != from.source } ?: return

        if (linkTaken(uuid, target)) {
            Log.w("Subscription move refused: another subscription has that link")
            return
        }

        profileLock.withLock {
            context.processingDir.deleteRecursively()
            context.processingDir.mkdirs()

            dir.copyRecursively(context.processingDir, overwrite = true)
        }

        val subscriptionInfo = try {
            val template = templateOf(from.type, ConfigTemplateStore(context).read(uuid))

            fetchProfile(context, target, true, null, brokenConfigAllowed(context, uuid, from.type), template)
        } catch (e: Exception) {
            // The kind only: the core's text carries the new link with the person's token.
            Log.w("Subscription move postponed, the new address failed: ${Failure.of(e.message).kind}")
            return
        }

        profileLock.withLock {
            val current = ImportedDao().queryByUUID(uuid)
            if (current == null || current.source != from.source) return@withLock

            ProviderInfo.write(
                context.processingDir,
                ProviderInfo.read(context.processingDir).copy(movedFrom = from.source, movedAt = System.currentTimeMillis()),
            )

            dir.replaceWithCopyOf(context.processingDir)
            ConfigAppsStore(context).dropIfLocked(uuid, dir)

            // A name that is the old link's domain was given at adding, not by the person (D-117): it follows the
            // link, or it would read as the person's own and hide the provider's title.
            val auto = current.name == Uri.parse(from.source).host.orEmpty()

            ImportedDao().update(
                current.copy(
                    source = target,
                    name = if (auto) Uri.parse(target).host.orEmpty() else current.name,
                    upload = subscriptionInfo?.subUpload ?: current.upload,
                    download = subscriptionInfo?.subDownload ?: current.download,
                    total = subscriptionInfo?.subTotal ?: current.total,
                    expire = subscriptionInfo?.subExpire ?: current.expire,
                )
            )

            context.sendProfileChanged(uuid)
        }
    }

    private suspend fun linkTaken(uuid: UUID, link: String): Boolean =
        ImportedDao().queryAllUUIDs().any { it != uuid && ImportedDao().queryByUUID(it)?.source == link } ||
            PendingDao().queryAllUUIDs().any { it != uuid && PendingDao().queryByUUID(it)?.source == link }

    private suspend fun <T> withProcessingLock(context: Context, block: suspend () -> T): T =
        processLock.withLock {
            try {
                block()
            } finally {
                context.processingDir.deleteRecursively()
            }
        }

    /**
     * Можно ли вместо конфига провайдера с ошибкой взять встроенный шаблон с его узлами (D-246).
     *
     * Да — пока рабочего конфига провайдера у подписки нет: её добавляют впервые или она и так
     * стоит на шаблоне по той же причине. У подписки, которая работает на конфиге провайдера,
     * остаётся он: в нём его маршрутизация, а неудачное обновление её не трогает.
     *
     * Только для подписок по ссылке. Свой файл конфига человек правит сам, и ему нужна ошибка,
     * а не подмена его настроек нашими.
     */
    private suspend fun brokenConfigAllowed(context: Context, uuid: UUID, type: Profile.Type): Boolean {
        if (type != Profile.Type.Url) return false
        if (ImportedDao().queryByUUID(uuid) == null) return true

        return ProviderInfo.read(context.importedDir.resolve(uuid.toString())).templateFromConfig
    }

    /**
     * A failed update keeps the old profile, yet the list of addresses shows this try (Q-82), and the screens its
     * failure (D-436). Not for a subscription deleted meanwhile: writing would make its folder again.
     */
    private fun recordTries(context: Context, uuid: UUID, tries: List<FetchStatus.Try>, failure: Exception) {
        val dir = context.importedDir.resolve(uuid.toString())
        if (!dir.isDirectory) return

        val now = System.currentTimeMillis()
        val info = ProviderInfo.read(dir)

        ProviderInfo.write(
            dir,
            info.copy(
                tries = tries.ifEmpty { info.tries },
                triedAt = if (tries.isEmpty()) info.triedAt else now,
                updateEndedAt = now,
                updateFailure = failure.message.orEmpty(),
            ),
        )
    }

    private fun markHwidBlocked(context: Context, uuid: UUID, failure: HwidBlockedException) {
        val dir = context.importedDir.resolve(uuid.toString())
        val current = ProviderInfo.read(dir)

        ProviderInfo.write(
            dir,
            current.copy(
                hwidBlocked = true,
                hwidReason = failure.reason,
                // Текст и ссылку берём из отказа, но прежние не затираем пустыми:
                // провайдер мог прислать их только в обычном ответе.
                announce = failure.announce.ifEmpty { current.announce },
                supportUrl = failure.supportUrl.ifEmpty { current.supportUrl },
            ),
        )
        // The panel's refusal is the provider's word too: «удалите старое устройство в боте».
        AnnouncementStore.record(context, uuid, failure.announce, current.announce)

        context.sendProfileChanged(uuid)
    }

    /** The announcement shown before this update and the one it brought, read before the commit replaces the first. */
    private fun announcements(context: Context, uuid: UUID): Pair<String, String> =
        ProviderInfo.read(context.importedDir.resolve(uuid.toString())).announce to
            ProviderInfo.read(context.processingDir).announce

    private suspend fun fetchProfile(
        context: Context,
        source: String,
        force: Boolean,
        callback: IFetchObserver?,
        brokenConfigAllowed: Boolean,
        template: ConfigTemplate? = null,
        tried: (List<FetchStatus.Try>) -> Unit = {},
    ): FetchStatus? {
        var subscriptionInfo: FetchStatus? = null
        var provider: ProviderInfo? = null
        var cb = callback

        // Заголовки устройства для запроса подписки (D-30). Ставятся перед каждой
        // загрузкой, а не один раз: выключатель отправки идентификатора может смениться
        // между обновлениями (D-36).
        Clash.setSubscriptionHeaders(SubscriptionHeaders.of(context, source))

        var fetchFailure: Exception? = null

        // The previous download's mirrors and the address that answered it: the core tries them in
        // this order (D-345).
        val previous = ProviderInfo.read(context.processingDir)
        var servedBy = ""
        var servedVia = ""
        var tries = emptyList<FetchStatus.Try>()

        Clash.setSubscriptionMirrors(
            source,
            SubscriptionMirrors.order(source, previous.mirrorsFor(source), previous.servedBy),
        )

        fun answered(headers: Map<String, String>) {
            val info = ProviderInfo.continued(ProviderInfo.of(headers, source), previous, headers, servedBy, servedVia)
                .copy(tries = tries, triedAt = System.currentTimeMillis())

            provider = info
            ProviderInfo.write(context.processingDir, info)
        }

        // Сведения провайдера — только от этого ответа. При обновлении в каталог обработки
        // скопирован прежний файл, и ответ без единого заголовка оставлял бы прежние сведения —
        // в том числе `ducker-local-proxy: off` (D-218). Без `force` готовый конфиг не
        // скачивается заново, и сведения при нём остаются.
        if (force) ProviderInfo.write(context.processingDir, ProviderInfo())

        Clash.fetchAndValid(context.processingDir, source, force) {
            if (it.action == FetchStatus.Action.SubscriptionInfo) {
                subscriptionInfo = it
                return@fetchAndValid
            }

            // These two come before the headers (`subscription_mirrors.go`).
            if (it.action == FetchStatus.Action.SubscriptionSource) {
                servedBy = it.args.getOrElse(0) { "" }
                servedVia = it.args.getOrElse(1) { "" }
                return@fetchAndValid
            }

            if (it.action == FetchStatus.Action.SubscriptionTries) {
                tries = it.tries.orEmpty()
                tried(tries)
                return@fetchAndValid
            }

            // Заголовки ответа пишем сразу в каталог обработки: оттуда они попадут в каталог
            // профиля тем же копированием, что и сам конфиг (D-32). Если дальше проверка
            // не пройдёт, каталог не копируется — и прежние сведения останутся на месте
            // вместе с прежним профилем.
            if (it.action == FetchStatus.Action.SubscriptionHeaders) {
                it.headers?.let(::answered)
                return@fetchAndValid
            }

            try {
                cb?.updateStatus(it)
            } catch (e: Exception) {
                cb = null

                Log.w("Report fetch status: $e", e)
            }
        }.runCatching { await() }.onFailure { fetchFailure = it as? Exception ?: Exception(it) }

        // The core passes up only the headers it knows (`collectHeaders`), and a plain mirror may send none
        // of them. Its response still keeps the list, the address that gave it and the tries, as one
        // without the mirror header does (D-345); `servedBy` is reported only for an address that answered.
        if (provider == null && servedBy.isNotEmpty()) answered(emptyMap())

        provider?.takeIf { it.hwidBlocked }?.let {
            throw HwidBlockedException(it.hwidReason, it.announce, it.supportUrl)
        }

        fetchFailure?.let { failure ->
            // Скачать не удалось: сервер ответил ошибкой (F-155), ответ не дошёл или он больше
            // предела — шаблону тут разбирать нечего. Текст ядра уходит как есть: словами его
            // объясняет `Failure` там, где ошибку показывают (D-227).
            if (Failure.of(failure.message).kind.download) throw failure

            // A template takes only the nodes, and a config the core refused may still have them.
            if (template != null) return@let

            val fromConfig = applyTemplateFallback(context, source, failure, callback, brokenConfigAllowed)

            // Without a download (a switch back) the directory holds the previous response's details.
            ProviderInfo.write(
                context.processingDir,
                (provider ?: ProviderInfo.read(context.processingDir)).copy(
                    usedTemplate = true,
                    lastError = failure.message.orEmpty(),
                    templateFromConfig = fromConfig,
                ),
            )
        }

        if (template != null) {
            buildFromTemplate(context, source, template, callback)

            // `templateFromConfig` is «no working provider config» (D-246) for a later return to the provider's.
            ProviderInfo.write(
                context.processingDir,
                (provider ?: ProviderInfo.read(context.processingDir)).copy(
                    template = template.id,
                    usedTemplate = false,
                    lastError = fetchFailure?.message.orEmpty(),
                    templateFromConfig = fetchFailure != null,
                ),
            )
        } else {
            context.processingDir.resolve(SOURCE_FILE).delete()

            ProviderInfo.read(context.processingDir).takeIf { it.template.isNotEmpty() }?.let {
                ProviderInfo.write(context.processingDir, it.copy(template = ""))
            }
        }

        return subscriptionInfo
    }

    /** The template [chosen] names, for a URL subscription: a file the person edits themselves (D-493). */
    private fun templateOf(type: Profile.Type, chosen: String): ConfigTemplate? {
        if (type != Profile.Type.Url || chosen.isEmpty()) return null

        return ConfigTemplate.byId(chosen) ?: throw IllegalArgumentException("Шаблон «$chosen» этой версии приложения неизвестен")
    }

    /**
     * Template mode (D-493): the provider's download is kept as [SOURCE_FILE], and `config.yaml` becomes the
     * template with its nodes and providers, checked by the core with the provider files fetched.
     */
    private suspend fun buildFromTemplate(context: Context, source: String, template: ConfigTemplate, callback: IFetchObserver?) {
        val config = context.processingDir.resolve("config.yaml")

        config.copyTo(context.processingDir.resolve(SOURCE_FILE), overwrite = true)

        val text = context.assets.open(template.asset).bufferedReader().use { it.readText() }
        val directHost = runCatching { Uri.parse(source).host.orEmpty() }.getOrDefault("")

        config.writeText(Clash.convertWithSources(config.readText(), text, directHost))

        var cb = callback

        Clash.fetchAndValid(context.processingDir, source, false) {
            try {
                cb?.updateStatus(it)
            } catch (e: Exception) {
                cb = null
            }
        }.await()
    }

    /**
     * [ProfileManager.setTemplate]. Not downloaded yet: only stored, its first download builds it so. Already in
     * effect: only stored, which also converges a choice a crash left behind.
     */
    suspend fun setTemplate(context: Context, uuid: UUID, id: String, callback: IFetchObserver?) {
        if (id.isNotEmpty()) requireNotNull(ConfigTemplate.byId(id)) { "Unsupported template $id" }

        val type = ImportedDao().queryByUUID(uuid)?.type ?: PendingDao().queryByUUID(uuid)?.type
            ?: throw IllegalArgumentException("profile $uuid not found")

        require(id.isEmpty() || type == Profile.Type.Url) { "Templates are for URL subscriptions only" }

        val stored = withContext(NonCancellable) {
            profileLock.withLock {
                val imported = ImportedDao().exists(uuid)
                val effective = ProviderInfo.read(context.importedDir.resolve(uuid.toString())).template

                if (!imported && PendingDao().queryByUUID(uuid) == null) return@withLock true
                if (imported && effective != id) return@withLock false

                ConfigTemplateStore(context).write(uuid, id)
                if (imported) context.sendProfileChanged(uuid)

                true
            }
        }

        if (!stored) update(context, uuid, callback, switchTo = id)
    }

    /**
     * Второй шаг D-19: по ссылке пришёл не конфиг, а набор ссылок протоколов.
     *
     * Ядро уже положило скачанное в config.yaml и отказалось его разбирать. Пробуем
     * прочесть это как список ссылок и подставить в наш шаблон. Получилось — проверяем
     * получившийся конфиг тем же ядром (загрузки не будет: файл на месте, force = false).
     * Не получилось — отдаём исходную ошибку ядра, она понятнее, чем «не разобралось».
     *
     * Если это не ссылки, а конфиг с ошибкой, узлы берутся из него — но только когда
     * [brokenConfigAllowed] (D-246); иначе та же исходная ошибка, и прежний профиль цел.
     *
     * @return true, если узлы взяты из конфига с ошибкой, а не из ссылок.
     */
    private suspend fun applyTemplateFallback(
        context: Context,
        source: String,
        original: Exception,
        callback: IFetchObserver?,
        brokenConfigAllowed: Boolean,
    ): Boolean {
        val config = context.processingDir.resolve("config.yaml")
        val raw = runCatching { config.readText() }.getOrNull()

        if (raw.isNullOrBlank()) throw original

        val template = runCatching {
            context.assets.open("ducker/template.yaml").bufferedReader().use { it.readText() }
        }.getOrElse { throw original }

        // Домен подписки отправляется мимо туннеля — иначе при мёртвых узлах
        // обновиться нельзя ровно тогда, когда это нужно (F-53).
        val directHost = runCatching { Uri.parse(source).host.orEmpty() }.getOrDefault("")

        val converted = runCatching {
            Clash.convertAndApplyTemplate(raw, template, directHost)
        }.getOrElse { throw original }

        if (converted.fromConfig && !brokenConfigAllowed) throw original

        config.writeText(converted.yaml)

        var cb = callback

        // Шаблон с узлами из конфига провайдера тоже может не пройти проверку — тогда человеку
        // нужна ошибка провайдера, а не наша.
        runCatching {
            Clash.fetchAndValid(context.processingDir, source, false) {
                try {
                    cb?.updateStatus(it)
                } catch (e: Exception) {
                    cb = null
                }
            }.await()
        }.getOrElse { throw if (converted.fromConfig) original else it }

        return converted.fromConfig
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                ImportedDao().remove(uuid)
                PendingDao().remove(uuid)

                val pending = context.pendingDir.resolve(uuid.toString())
                val imported = context.importedDir.resolve(uuid.toString())

                pending.deleteRecursively()
                imported.deleteRecursively()
                AnnouncementStore.forget(context, uuid)
                ConfigTemplateStore(context).delete(uuid)

                context.sendProfileChanged(uuid)
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                val deleted = context.pendingDir.resolve(uuid.toString()).deleteRecursively()

                // A subscription given up before its first download leaves no choice behind.
                if (!ImportedDao().exists(uuid)) ConfigTemplateStore(context).delete(uuid)

                deleted
            }
        }
    }

    suspend fun active(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                if (ImportedDao().exists(uuid)) {
                    val store = ServiceStore(context)

                    store.activeProfile = uuid

                    context.sendProfileChanged(uuid)
                }
            }
        }
    }

    private fun Pending.enforceFieldValid() {
        val scheme = Uri.parse(source)?.scheme?.lowercase(Locale.getDefault())

        when {
            name.isBlank() -> throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File -> throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" -> throw IllegalArgumentException(
                "Unsupported url $source"
            )

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 -> throw IllegalArgumentException("Invalid interval")
        }
    }

}
