package com.github.kr328.clash.ui.config

/** Внешний редактор принимает только content URI стороннего поставщика файлов. */
internal fun externalConfigUriAllowed(scheme: String?, authority: String?, packageName: String): Boolean {
    if (scheme != "content" || authority.isNullOrBlank()) return false

    // Android может добавить номер пользователя перед authority: 0@app.ducker.android.files.
    val provider = authority.substringAfterLast('@')
    return provider.isNotBlank() && !provider.equals(packageName, ignoreCase = true) &&
        !provider.startsWith("$packageName.", ignoreCase = true)
}
