package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.service.util.ProviderInfo
import java.io.File
import java.util.UUID

/** A bundled configuration template: the subscription gives the nodes, the template everything else. */
data class ConfigTemplate(val id: String, val asset: String) {
    companion object {
        val all = listOf(ConfigTemplate("ducker-ru", "ducker/template.yaml"))

        fun byId(id: String): ConfigTemplate? = all.firstOrNull { it.id == id }
    }
}

/**
 * The template the person chose per subscription; no file — «Из подписки». Outside the profile's directory, which
 * every update rebuilds, as the person's rules are. The template that is in effect is [ProviderInfo.template]
 * inside that directory, committed by the same swap as the config it describes.
 */
class ConfigTemplateStore(context: Context) {
    private val dir = context.filesDir.resolve("config-template")

    fun read(uuid: UUID): String = runCatching { file(uuid).readText().trim() }.getOrDefault("")

    fun write(uuid: UUID, id: String) {
        if (id.isEmpty()) {
            delete(uuid)

            return
        }

        dir.mkdirs()

        // Through a temporary file: an update may read it that very second.
        val temp = File(dir, "$uuid.tmp")

        temp.writeText(id)
        temp.renameTo(file(uuid))
    }

    fun delete(uuid: UUID) {
        file(uuid).delete()
    }

    private fun file(uuid: UUID) = File(dir, uuid.toString())
}

/** The provider's download in template mode; `config.yaml` beside it is generated. */
const val SOURCE_FILE = "source.yaml"

/**
 * What a switch of [profileDir] to [template] ("" — the subscription's own config) rebuilds from without a download:
 * the kept provider's download, or — into template mode — a config that is the provider's own. Null: it downloads.
 * A source is never made up from a generated config.
 */
fun localSource(profileDir: File, template: String): File? {
    profileDir.resolve(SOURCE_FILE).takeIf { it.isFile }?.let { return it }

    val info = ProviderInfo.read(profileDir)
    val config = profileDir.resolve("config.yaml")

    return config.takeIf {
        template.isNotEmpty() && info.template.isEmpty() && !info.usedTemplate && it.isFile && it.length() > 0
    }
}
