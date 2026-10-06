package com.github.kr328.clash.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** Вызывается только в процессе ядра до его загрузки. Старую базу сохраняем до замены. */
internal fun extractGeoAsset(file: File, updateDate: Long, openAsset: () -> InputStream) {
    if (file.isFile && file.lastModified() >= updateDate) return

    // Один и тот же временный файл: после убийства процесса следующая попытка его перезапишет.
    val temp = File(file.parentFile, "${file.name}.tmp")
    try {
        openAsset().use { input ->
            FileOutputStream(temp).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (!temp.setLastModified(updateDate)) throw IOException("Cannot timestamp geo asset ${file.name}")
        if (!temp.renameTo(file)) throw IOException("Cannot replace geo asset ${file.name}")
    } finally {
        temp.delete()
    }
}
