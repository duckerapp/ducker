package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.SubscriptionHeaders
import com.github.kr328.clash.service.util.SubscriptionMirrors
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    private val profileLock = Mutex()
    private val processLock = Mutex()

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

                Clash.setAgeSecretKey(snapshot.ageSecretKey?.takeIf { it.isNotBlank() })

                val force = snapshot.type != Profile.Type.File
                val subscriptionInfo =
                    fetchProfile(context, snapshot.source, force, callback, brokenConfigAllowed(context, snapshot.uuid, snapshot.type))

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir.copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

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

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
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

                Clash.setAgeSecretKey(snapshot.ageSecretKey?.takeIf { it.isNotBlank() })

                var tries = emptyList<FetchStatus.Try>()

                val subscriptionInfo = try {
                    fetchProfile(context, snapshot.source, true, callback, brokenConfigAllowed(context, snapshot.uuid, snapshot.type)) {
                        tries = it
                    }
                } catch (e: HwidBlockedException) {
                    // Профиль остаётся прежним, но отметку об отказе нужно сохранить рядом
                    // с ним: иначе человек видит только то, что «ничего не изменилось»,
                    // а причину — нигде (D-26). Следующее удачное обновление перезапишет
                    // сведения провайдера целиком и отметка сама исчезнет.
                    markHwidBlocked(context, snapshot.uuid, e)
                    recordTries(context, snapshot.uuid, tries)

                    throw e
                } catch (e: Exception) {
                    recordTries(context, snapshot.uuid, tries)

                    throw e
                }

                profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(snapshot.uuid)
                    if (imported != null) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir.copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

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
            fetchProfile(context, target, true, null, brokenConfigAllowed(context, uuid, from.type))
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

            dir.deleteRecursively()
            context.processingDir.copyRecursively(dir)

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

    /** A failed update keeps the old profile, yet the list of addresses shows this try (Q-82). */
    private fun recordTries(context: Context, uuid: UUID, tries: List<FetchStatus.Try>) {
        if (tries.isEmpty()) return

        val dir = context.importedDir.resolve(uuid.toString())

        ProviderInfo.write(dir, ProviderInfo.read(dir).copy(tries = tries, triedAt = System.currentTimeMillis()))
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

        context.sendProfileChanged(uuid)
    }

    private suspend fun fetchProfile(
        context: Context,
        source: String,
        force: Boolean,
        callback: IFetchObserver?,
        brokenConfigAllowed: Boolean,
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
                it.headers?.let { headers ->
                    val info = ProviderInfo.continued(ProviderInfo.of(headers, source), previous, headers, servedBy, servedVia)
                        .copy(tries = tries, triedAt = System.currentTimeMillis())

                    provider = info
                    ProviderInfo.write(context.processingDir, info)
                }
                return@fetchAndValid
            }

            try {
                cb?.updateStatus(it)
            } catch (e: Exception) {
                cb = null

                Log.w("Report fetch status: $e", e)
            }
        }.runCatching { await() }.onFailure { fetchFailure = it as? Exception ?: Exception(it) }

        provider?.takeIf { it.hwidBlocked }?.let {
            throw HwidBlockedException(it.hwidReason, it.announce, it.supportUrl)
        }

        fetchFailure?.let { failure ->
            // Скачать не удалось: сервер ответил ошибкой (F-155), ответ не дошёл или он больше
            // предела — шаблону тут разбирать нечего. Текст ядра уходит как есть: словами его
            // объясняет `Failure` там, где ошибку показывают (D-227).
            if (Failure.of(failure.message).kind.download) throw failure

            val fromConfig = applyTemplateFallback(context, source, failure, callback, brokenConfigAllowed)

            ProviderInfo.write(
                context.processingDir,
                (provider ?: ProviderInfo()).copy(
                    usedTemplate = true,
                    lastError = failure.message.orEmpty(),
                    templateFromConfig = fromConfig,
                ),
            )
        }

        return subscriptionInfo
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

                context.sendProfileChanged(uuid)
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                context.pendingDir.resolve(uuid.toString()).deleteRecursively()
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
