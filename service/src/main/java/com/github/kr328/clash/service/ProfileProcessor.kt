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
                val subscriptionInfo = fetchProfile(context, snapshot.source, force, callback)

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir.copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        val updateInterval = subscriptionInfo?.subUpdateInterval
                            ?.takeIf { old == null && snapshot.interval == 0L }
                            ?: snapshot.interval
                        val new = Imported(
                            snapshot.uuid,
                            snapshot.name,
                            snapshot.type,
                            snapshot.source,
                            updateInterval,
                            subscriptionInfo?.subUpload ?: 0,
                            subscriptionInfo?.subDownload ?: 0,
                            subscriptionInfo?.subTotal ?: 0,
                            subscriptionInfo?.subExpire ?: 0,
                            old?.createdAt ?: System.currentTimeMillis(),
                            ageSecretKey = snapshot.ageSecretKey
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

                val subscriptionInfo = try {
                    fetchProfile(context, snapshot.source, true, callback)
                } catch (e: HwidBlockedException) {
                    // Профиль остаётся прежним, но отметку об отказе нужно сохранить рядом
                    // с ним: иначе человек видит только то, что «ничего не изменилось»,
                    // а причину — нигде (D-26). Следующее удачное обновление перезапишет
                    // сведения провайдера целиком и отметка сама исчезнет.
                    markHwidBlocked(context, snapshot.uuid, e)

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
            }
        }
    }

    private suspend fun <T> withProcessingLock(context: Context, block: suspend () -> T): T =
        processLock.withLock {
            try {
                block()
            } finally {
                context.processingDir.deleteRecursively()
            }
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
    ): FetchStatus? {
        var subscriptionInfo: FetchStatus? = null
        var provider: ProviderInfo? = null
        var cb = callback

        // Заголовки устройства для запроса подписки (D-30). Ставятся перед каждой
        // загрузкой, а не один раз: выключатель отправки идентификатора может смениться
        // между обновлениями (D-36).
        Clash.setSubscriptionHeaders(SubscriptionHeaders.of(context, source))

        var fetchFailure: Exception? = null

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

            // Заголовки ответа пишем сразу в каталог обработки: оттуда они попадут в каталог
            // профиля тем же копированием, что и сам конфиг (D-32). Если дальше проверка
            // не пройдёт, каталог не копируется — и прежние сведения останутся на месте
            // вместе с прежним профилем.
            if (it.action == FetchStatus.Action.SubscriptionHeaders) {
                it.headers?.let { headers ->
                    val info = ProviderInfo.of(headers)

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

            val reason = applyTemplateFallback(context, source, failure, callback)

            ProviderInfo.write(
                context.processingDir,
                (provider ?: ProviderInfo()).copy(usedTemplate = true, lastError = reason),
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
     * @return текст ошибки ядра, если шаблон пригодился; null, если профиль обычный.
     */
    private suspend fun applyTemplateFallback(
        context: Context,
        source: String,
        original: Exception,
        callback: IFetchObserver?,
    ): String {
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

        config.writeText(converted)

        var cb = callback

        Clash.fetchAndValid(context.processingDir, source, false) {
            try {
                cb?.updateStatus(it)
            } catch (e: Exception) {
                cb = null
            }
        }.await()

        return original.message.orEmpty()
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
