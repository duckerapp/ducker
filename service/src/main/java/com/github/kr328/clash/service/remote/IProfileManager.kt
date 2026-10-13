package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.model.Profile
import com.github.kr328.kaidl.BinderInterface
import java.util.*

@BinderInterface
interface IProfileManager {
    suspend fun create(type: Profile.Type, name: String, source: String = "", ageSecretKey: String? = null): UUID

    /**
     * Создаёт профиль из набора ссылок протоколов (D-17): человек вставил `vless://…`
     * или подписку в base64, скачивать нечего.
     *
     * Конвертация идёт здесь, а не в интерфейсе, намеренно: она делается ядром, а ядро
     * живёт в процессе службы. Тронуть его из процесса интерфейса — значит загрузить туда
     * libclash.so целиком и потерять смысл разделения процессов (FACTS.md, F-5).
     *
     * The text comes as a file at [path], which the caller writes and deletes: the arguments of a
     * command are one Binder transaction, and a list over about 520,000 characters did not fit
     * into it (F-268).
     */
    suspend fun createFromContent(name: String, path: String): UUID
    suspend fun clone(uuid: UUID): UUID
    suspend fun commit(uuid: UUID, callback: IFetchObserver? = null)
    suspend fun release(uuid: UUID)
    suspend fun delete(uuid: UUID)
    suspend fun patch(uuid: UUID, name: String, source: String, interval: Long, ageSecretKey: String?)
    suspend fun update(uuid: UUID)
    suspend fun queryByUUID(uuid: UUID): Profile?
    suspend fun queryAll(): List<Profile>
    suspend fun queryActive(): Profile?
    suspend fun setActive(profile: Profile)
}
