package com.github.kr328.clash.service

import com.github.kr328.clash.service.clash.module.MobileModule
import com.github.kr328.clash.service.store.ConfigAppsStore
import com.github.kr328.clash.service.store.UserRulesStore
import android.content.Context
import com.github.kr328.clash.common.util.backgroundScope
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.directoryLastModified
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.service.util.ProviderInfo
import com.github.kr328.clash.service.util.SubscriptionMirrors
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.TimeUnit

/** Тот же текст, что у ядра (`convert/convert.go`): по нему `Failure` узнаёт «это не ссылки». */
private const val NOT_LINKS = "содержимое не похоже на список ссылок протоколов"

class ProfileManager(private val context: Context) : IProfileManager,
    CoroutineScope by backgroundScope("ProfileManager") {
    private val store = ServiceStore(context)

    init {
        launch {
            Database.database //.init

            ProfileReceiver.rescheduleAll(context)
        }
    }

    override suspend fun create(type: Profile.Type, name: String, source: String, ageSecretKey: String?): UUID {
        val uuid = generateProfileUUID()
        val pending = Pending(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            interval = 0,
            upload = 0,
            total = 0,
            download = 0,
            expire = 0,
            ageSecretKey = ageSecretKey,
        )

        PendingDao().insert(pending)

        context.pendingDir.resolve(uuid.toString()).apply {
            deleteRecursively()
            mkdirs()

            @Suppress("BlockingMethodInNonBlockingContext")
            resolve("config.yaml").createNewFile()
            resolve("providers").mkdir()
        }

        return uuid
    }

    override suspend fun createFromContent(name: String, path: String): UUID {
        val content = withContext(Dispatchers.IO) { File(path).readText() }
        val template = withContext(Dispatchers.IO) {
            context.assets.open("ducker/template.yaml").bufferedReader().use { it.readText() }
        }

        // Конвертируем до создания профиля: если пришёл мусор, не остаётся висящей записи,
        // а человек видит ошибку ядра.
        val converted = Clash.convertAndApplyTemplate(content, template)

        // Сюда вставляют ссылки. Конфиг целиком добавляют файлом, и подменять его шаблоном
        // незачем — отказ тот же, что и раньше.
        if (converted.fromConfig) throw ClashException(NOT_LINKS)

        val config = converted.yaml

        val uuid = create(Profile.Type.File, name)

        withContext(Dispatchers.IO) {
            context.pendingDir.resolve(uuid.toString()).resolve("config.yaml").writeText(config)
        }

        return uuid
    }

    override suspend fun clone(uuid: UUID): UUID {
        val newUUID = generateProfileUUID()

        val imported = ImportedDao().queryByUUID(uuid)
            ?: throw FileNotFoundException("profile $uuid not found")

        val pending = Pending(
            uuid = newUUID,
            name = imported.name,
            type = Profile.Type.File,
            source = imported.source,
            interval = imported.interval,
            upload = imported.upload,
            total = imported.total,
            download = imported.download,
            expire = imported.expire,
            ageSecretKey = imported.ageSecretKey
        )

        cloneImportedFiles(uuid, newUUID)

        PendingDao().insert(pending)

        return newUUID
    }

    override suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, followProvider: Boolean, ageSecretKey: String?) {
        val pending = PendingDao().queryByUUID(uuid)

        if (pending == null) {
            val imported = ImportedDao().queryByUUID(uuid)
                ?: throw FileNotFoundException("profile $uuid not found")

            cloneImportedFiles(uuid)

            PendingDao().insert(
                Pending(
                    uuid = imported.uuid,
                    name = name,
                    type = imported.type,
                    source = source,
                    interval = interval,
                    upload = 0,
                    total = 0,
                    download = 0,
                    expire = 0,
                    ageSecretKey = ageSecretKey,
                    followProvider = followProvider,
                )
            )
        } else {
            val newPending = pending.copy(
                name = name,
                source = source,
                interval = interval,
                upload = 0,
                total = 0,
                download = 0,
                expire = 0,
                ageSecretKey = ageSecretKey,
                followProvider = followProvider,
            )

            PendingDao().update(newPending)
        }
    }

    override suspend fun update(uuid: UUID) {
        // Подписка, которая ни разу не загрузилась, лежит в «ожидающих», и планировщику
        // обновлений она неизвестна: `scheduleUpdate` молча возвращался, приложение
        // оставалось ждать ответа, которого не будет, и карточка крутилась вечно (D-83).
        check(ImportedDao().exists(uuid)) { "profile $uuid is not imported" }

        scheduleUpdate(uuid, true)
    }

    override suspend fun commit(uuid: UUID, callback: IFetchObserver?) {
        ProfileProcessor.apply(context, uuid, callback)

        scheduleUpdate(uuid, false)
    }

    override suspend fun release(uuid: UUID) {
        ProfileProcessor.release(context, uuid)
    }

    override suspend fun delete(uuid: UUID) {
        ImportedDao().queryByUUID(uuid)?.also {
            ProfileReceiver.cancelNext(context, it)
        }

        ProfileProcessor.delete(context, uuid)

        // Свои правила уходят вместе с подпиской (D-123), и изменённые приложения конфига тоже (D-471).
        UserRulesStore(context).delete(uuid)
        ConfigAppsStore(context).delete(uuid)
    }

    override suspend fun queryByUUID(uuid: UUID): Profile? {
        return resolveProfile(uuid)
    }

    override suspend fun queryAll(): List<Profile> {
        val uuids = withContext(Dispatchers.IO) {
            (ImportedDao().queryAllUUIDs() + PendingDao().queryAllUUIDs()).distinct()
        }

        return uuids.mapNotNull { resolveProfile(it) }
    }

    override suspend fun queryActive(): Profile? {
        val active = store.activeProfile ?: return null

        return if (ImportedDao().exists(active)) {
            resolveProfile(active)
        } else {
            null
        }
    }

    override suspend fun setActive(profile: Profile) {
        // The person's own pick holds against «Мобильная сеть» until the network changes (D-424).
        if (profile.uuid != store.activeProfile) MobileModule.manual(context)

        ProfileProcessor.active(context, profile.uuid)
    }

    private suspend fun resolveProfile(uuid: UUID): Profile? {
        val imported = ImportedDao().queryByUUID(uuid)
        val pending = PendingDao().queryByUUID(uuid)

        val active = store.activeProfile
        val name = pending?.name ?: imported?.name ?: return null
        val type = pending?.type ?: imported?.type ?: return null
        val source = pending?.source ?: imported?.source ?: return null
        val interval = pending?.interval ?: imported?.interval ?: return null
        val upload = pending?.upload ?: imported?.upload ?: return null
        val download = pending?.download ?: imported?.download ?: return null
        val total = pending?.total ?: imported?.total ?: return null
        val expire = pending?.expire ?: imported?.expire ?: return null
        val followProvider = pending?.followProvider ?: imported?.followProvider ?: return null

        // Сведения от провайдера лежат файлом в каталоге профиля (D-32), а не в базе.
        val provider = ProviderInfo.read(context.importedDir.resolve(uuid.toString()))
        val moveShown = provider.source == source && System.currentTimeMillis() - provider.movedAt < ProviderInfo.MOVED_SHOWN

        return Profile(
            uuid = uuid,
            name = name,
            type = type,
            source = source,
            active = active != null && imported?.uuid == active,
            interval = interval,
            upload = upload,
            download = download,
            total = total,
            expire = expire,
            updatedAt = resolveUpdatedAt(uuid),
            createdAt = imported?.createdAt ?: 0,
            imported = imported != null,
            pending = pending != null,
            ageSecretKey = if (pending != null) pending.ageSecretKey else imported?.ageSecretKey,
            profileTitle = provider.profileTitle,
            announce = provider.announce,
            supportUrl = provider.supportUrl,
            profileWebPageUrl = provider.profileWebPageUrl,
            brandName = provider.brandName,
            brandLogo = provider.brandLogo,
            buyUrl = provider.buyUrl,
            overrideUser = provider.overrideUser,
            globalHidden = provider.globalHidden,
            refillAt = provider.refillAt,
            hwidBlocked = provider.hwidBlocked,
            hwidReason = provider.hwidReason,
            usedTemplate = provider.usedTemplate,
            lastError = provider.lastError,
            templateFromConfig = provider.templateFromConfig,
            followProvider = followProvider,
            providerInterval = TimeUnit.HOURS.toMillis(provider.updateIntervalHours.toLong()),
            // Only what was learnt for this very link: after an edit of the link the old file speaks of
            // another one (D-345).
            backups = provider.mirrorsFor(source).size,
            mirrors = provider.mirrorsFor(source),
            movedFrom = if (moveShown) SubscriptionMirrors.host(provider.movedFrom) else "",
            movedAt = if (moveShown) provider.movedAt else 0,
            // Only where the provider gave mirrors: elsewhere there is no list to show (Q-82).
            addresses = provider.mirrorsFor(source).takeIf { it.isNotEmpty() }?.let { mirrors ->
                SubscriptionMirrors.addresses(source, mirrors, provider.tries, provider.servedBy)
            }.orEmpty(),
            triedAt = if (provider.source == source) provider.triedAt else 0,
            updateEndedAt = provider.updateEndedAt,
            updateFailure = provider.updateFailure,
        )
    }

    private fun resolveUpdatedAt(uuid: UUID): Long {
        return context.pendingDir.resolve(uuid.toString()).directoryLastModified
            ?: context.importedDir.resolve(uuid.toString()).directoryLastModified
            ?: -1
    }

    private fun cloneImportedFiles(source: UUID, target: UUID = source) {
        val s = context.importedDir.resolve(source.toString())
        val t = context.pendingDir.resolve(target.toString())

        if (!s.exists())
            throw FileNotFoundException("profile $source not found")

        t.deleteRecursively()

        s.copyRecursively(t)
    }

    private suspend fun scheduleUpdate(uuid: UUID, startImmediately: Boolean) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        if (startImmediately) {
            ProfileReceiver.schedule(context, imported)
        } else {
            ProfileReceiver.scheduleNext(context, imported)
        }
    }
}
