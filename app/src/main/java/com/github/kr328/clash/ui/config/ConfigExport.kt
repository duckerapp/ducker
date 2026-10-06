package com.github.kr328.clash.ui.config

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.serializer.AnchorGenerator
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Конфиг «без серверов» — чтобы показать в чате, как устроены группы, правила и DNS (D-124,
 * D-192). Это весь конфиг, кроме чувствительного:
 * - `proxies` и `proxy-providers` пустые: там адреса, ключи и ссылки подписок;
 * - доступ к локальному прокси и к панели: `authentication`, `secret`, `external-controller`,
 *   пользователи и пароли в `listeners`;
 * - адреса серверов, где бы они ни встретились: правило, запись `hosts`, имя узла в группе;
 * - логин, пароль и ключи внутри ссылок;
 * - комментарии: в них бывают закомментированные серверы.
 *
 * Убираются известные поля, а не «всё секретное», поэтому экран просит проглядеть файл перед
 * отправкой. YAML правится узлами, а не значениями: кавычки, порядок, якоря и написание чисел
 * остаются как в исходнике. Не разобрался или устроен необычно — экспорта нет.
 */
internal fun withoutServers(text: String): String = try {
    val root = Yaml(LOADER).compose(StringReader(text)) as? MappingNode ?: throw IllegalArgumentException()
    val addresses = HashSet<String>()
    // Узлы разделов с серверами: якорь оттуда не должен уцелеть под другим ключом.
    val removed = identitySet()
    val seen = identitySet()

    fun servers(node: Node, drop: Boolean) {
        if (node is ScalarNode || !(if (drop) removed else seen).add(node)) return
        if (node is SequenceNode) node.value.forEach { servers(it, drop) }
        if (node is MappingNode) for (tuple in node.value) {
            val key = tuple.keyNode.key()
            val value = (tuple.valueNode as? ScalarNode)?.value.orEmpty()
            when (key) {
                "server" -> value.trim('[', ']')
                // Домен панели: ссылка подписки ведёт на него.
                "url" -> runCatching { URI(value.trim()).host }.getOrNull()?.trim('[', ']')
                else -> null
            }?.takeIf { it.isNotBlank() }?.let(addresses::add)
            // `payload` — серверы, вписанные прямо в провайдера.
            servers(tuple.valueNode, drop || key == "payload")
        }
    }

    root.value = root.value.map { tuple ->
        when (tuple.keyNode.key()) {
            "proxies" -> {
                servers(tuple.valueNode, true)
                NodeTuple(tuple.keyNode, SequenceNode(Tag.SEQ, mutableListOf(), DumperOptions.FlowStyle.FLOW))
            }
            "proxy-providers" -> {
                servers(tuple.valueNode, false)
                NodeTuple(tuple.keyNode, MappingNode(Tag.MAP, mutableListOf(), DumperOptions.FlowStyle.FLOW))
            }
            // Разделы верхнего уровня, подставленные через `<<`, по имени не найти.
            "<<" -> throw IllegalArgumentException()
            else -> tuple
        }
    }

    // Субдомен сервера (`+.node.example`) — тоже его адрес; чужое слово с тем же хвостом — нет.
    val address = addresses.takeIf { it.isNotEmpty() }?.sortedByDescending { it.length }
        ?.joinToString("|") { Regex.escape(it) }?.let { Regex("""(?i)(?<![\w-])(?:$it)(?![\w-]|\.\w)""") }
    val cleaned = identitySet()

    // null — убрать вместе с ключом или местом в списке.
    fun clean(node: Node): Node? {
        when {
            node in removed -> return null
            node is ScalarNode -> return if (address?.containsMatchIn(node.value) == true) null else withoutCredentials(node)
            !cleaned.add(node) -> return node
            node is SequenceNode -> node.value.mapNotNull(::clean).let { node.value.clear(); node.value.addAll(it) }
            node is MappingNode -> node.value = node.value.mapNotNull { tuple ->
                val key = tuple.keyNode.key()
                if (key in PRIVATE_KEYS || key?.startsWith("external-controller") == true) return@mapNotNull null
                if (clean(tuple.keyNode) == null) null else clean(tuple.valueNode)?.let { NodeTuple(tuple.keyNode, it) }
            }
        }
        return node
    }
    clean(root)

    StringWriter().also { Yaml(dumper()).serialize(root, it) }.toString()
} catch (_: Exception) {
    // Ошибки YAML могут включать сырой секрет и строку исходника: не показываем их.
    throw IllegalArgumentException("Cannot safely export this config")
}

private fun identitySet(): MutableSet<Node> = Collections.newSetFromMap(IdentityHashMap())

/** Имя ключа, как его понимает ядро: без регистра, `_` и `-` равны. */
private fun Node.key(): String? = (this as? ScalarNode)?.value?.lowercase()?.replace('_', '-')

private fun withoutCredentials(node: ScalarNode): ScalarNode {
    val value = node.value.replace(URL_USER, "$1").replace(URL_SECRET, "$1")

    return if (value == node.value) node else ScalarNode(node.tag, value, node.startMark, node.endMark, node.scalarStyle)
}

// Узлы YAML общие, а не развёрнутые: ссылок на якоря может быть сколько угодно.
private val LOADER = LoaderOptions().apply {
    maxAliasesForCollections = Int.MAX_VALUE
    nestingDepthLimit = 50
    codePointLimit = 8 * 1024 * 1024
}

private fun dumper() = DumperOptions().apply {
    splitLines = false
    indicatorIndent = 2
    indentWithIndicator = true
    // Имена якорей — как в исходнике; повтор имени (YAML это разрешает) получает номер.
    val names = HashSet<String>()
    anchorGenerator = AnchorGenerator { node ->
        val name = node.anchor ?: "a"
        if (names.add(name)) name else generateSequence(2) { it + 1 }.map { "$name$it" }.first(names::add)
    }
}

private val PRIVATE_KEYS = setOf(
    "password", "secret", "uuid", "username", "users", "authentication", "authorization", "auth", "auth-str",
    "token", "psk", "private-key", "public-key", "short-id", "pre-shared-key", "obfs-password", "client-id",
)
private val URL_USER = Regex("""(?i)\b([a-z][a-z0-9+.-]*://)[^\s/?#@]+@""")
private val URL_SECRET = Regex("""(?i)([?&](?:token|key|secret|password|passwd|auth|access[-_]token|api[-_]?key|sig|signature)=)[^&#\s]*""")
