package com.github.kr328.clash.baselineprofile

import android.widget.EditText
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.regex.Pattern
import kotlin.concurrent.thread

/**
 * Сценарий, по которому снимается baseline profile: первый запуск, добавление подписки,
 * включение VPN и обход экранов, куда человек заходит в первые минуты.
 *
 * Профиль нужен именно за этим — чтобы ART скомпилировал заранее то, что выполняется на
 * старте и при первых нажатиях, а не интерпретировал.
 *
 * Без подписки приложение показывает приветствие и вкладки не листает (D-98), поэтому
 * сценарий добавляет проверочную подписку сам и сам же её раздаёт, с того же устройства:
 * сервер на компьютере не нужен. Каждый шаг ждёт свой экран и роняет тест, если его нет, —
 * молча пройти мимо значит снять пустой профиль.
 *
 * Запускать только на эмуляторе, скриптом `scripts/baseline-profile.sh`: данные приложения
 * стираются (F-86).
 */
class StartupProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        // Имя пакета обычно передаёт плагин, но полагаться только на него нельзя:
        // при запуске теста напрямую аргумента нет, а имя у нас всё равно одно.
        val target = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "app.ducker.android"

        val server = serveSubscription()
        var fresh = true

        try {
            rule.collect(packageName = target, includeInStartupProfile = true) {
                if (fresh) {
                    fresh = false

                    // Первый проход — с чистых данных, что бы ни стояло на эмуляторе: приветствие
                    // и первое добавление подписки тоже должны попасть в профиль.
                    device.executeShellCommand("pm clear $target")
                    device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
                    // Подписи сценарий ищет по-английски, какой бы язык ни стоял в системе.
                    device.executeShellCommand("cmd locale set-app-locales $target --locales en")
                }

                pressHome()
                startActivityAndWait()

                if (device.wait(Until.hasObject(By.text("Add subscription")), 3_000)) {
                    addSubscription("http://${ownAddress()}:${server.localPort}/sub.yaml")
                }

                tour()
            }
        } finally {
            server.close()
        }
    }

    /** Первое добавление — тем же путём, что у человека: кнопка, ссылка в поле, «Добавить». */
    private fun MacrobenchmarkScope.addSubscription(link: String) {
        tap(By.text("Add subscription"))
        need(By.clazz(EditText::class.java)).text = link
        // Кнопка оживает, когда поле приняло ссылку; нажатие раньше уходит в пустоту.
        need(By.text(link))
        tap(By.text("Add"))

        // Панель уезжает, только когда подписка скачана и принята ядром; с ошибкой она остаётся.
        check(device.wait(Until.gone(By.clazz(EditText::class.java)), 30_000)) { "Подписка не добавилась. ${seen()}" }
    }

    private fun MacrobenchmarkScope.tour() {
        val width = device.displayWidth
        val middle = device.displayHeight / 2

        // The dock is narrower than the screen and its width follows the labels: reach a tab by its label.
        fun home() = device.findObject(By.text("Home"))?.click()

        // Листание пальцем — основной способ переключать вкладки (D-76). Код листалки и
        // жестов должен попасть в профиль: без него первый же свайп идёт интерпретатором.
        fun nextTab() {
            device.waitForIdle()
            device.swipe(width * 5 / 6, middle, width / 6, middle, 8)
        }

        // Ленивый список подтягивает свои пути отдельно от всего остального.
        fun scroll() {
            repeat(2) { device.swipe(width / 2, middle + middle / 2, width / 2, middle / 2, 8); device.waitForIdle() }
            repeat(2) { device.swipe(width / 2, middle / 2, width / 2, middle + middle / 2, 8); device.waitForIdle() }
        }

        // Главная. Первое включение VPN: Android спрашивает разрешение, а приложение следом
        // предлагает разрешить работу в фоне — эту панель закрывает «назад».
        reach(By.desc("Connect")) { home() }
        reach(By.desc("Disconnect")) {
            device.findObject(By.desc("Connect"))?.click()
            device.wait(Until.findObject(By.res("android:id/button1")), 2_000)?.click()

            if (device.wait(Until.hasObject(By.text("Keep the VPN running")), 4_000)) device.pressBack()
        }

        // «Прокси»: прокрутка, замер задержек и выбор узла.
        // The tab starts its own check when it opens (D-401): the button reads «Checking…» until it ends.
        reach(By.desc(Pattern.compile("Check.*"))) { nextTab() }
        device.wait(Until.gone(By.desc(CHECKING)), 20_000)
        scroll()
        tap(By.desc("Check"))
        device.wait(Until.hasObject(By.desc(CHECKING)), 2_000)
        check(device.wait(Until.gone(By.desc(CHECKING)), 20_000)) { "Замер задержек не закончился. ${seen()}" }
        // Имя выбранного узла стоит на экране дважды — на карточке и в шапке группы. Берём
        // невыбранный: от прошлого прохода выбор остаётся, а нажатие на шапку группу сворачивает.
        val node = checkNotNull(NODES.map(::named).firstOrNull { device.findObjects(it).size == 1 }) {
            "Нет невыбранного узла. ${seen()}"
        }
        tap(node)
        check(device.wait(Condition<UiDevice, Boolean> { it.findObjects(node).size > 1 }, 10_000)) {
            "Узел не выбрался. ${seen()}"
        }

        // «Подписки»: правка и «Мои правила». On a phone the card's pencil is a round button named only for TalkBack.
        reach(By.desc("Edit")) { nextTab() }
        reach(By.text("My rules")) { tap(By.desc("Edit")) }
        reach(By.text("Add rule")) { tap(By.text("My rules")) }
        device.pressBack()
        need(By.desc("Edit"))

        reach(By.desc("Settings")) { home() }

        // Quick node selection: the sheet opens from the node row on Home, which shows the node
        // chosen above. Only up the list: a pull down at its top would drag the sheet closed.
        reach(By.text("All nodes")) { tap(node) }
        device.swipe(width / 2, middle + middle / 2, width / 2, middle, 8)
        tap(By.text("Close"))
        need(By.desc("Settings"))

        // Настройки открываются шестерёнкой в шапке главной; «Приложения» — на странице «Connection».
        reach(By.text("Connection")) { tap(By.desc("Settings")) }
        reach(By.text("Apps")) { tap(By.text("Connection")) }
        scroll()
        reach(By.text(Pattern.compile("Bypass VPN|Via VPN"))) { tap(By.text("Apps")) }
        scroll()
        device.pressBack()
        need(By.text("Apps"))
        device.pressBack()
        need(By.text("Connection"))
        device.pressBack()

        reach(By.desc("Connect")) { device.findObject(By.desc("Disconnect"))?.click() }
    }

    private fun MacrobenchmarkScope.need(what: BySelector, timeout: Long = 10_000) =
        checkNotNull(device.wait(Until.findObject(what), timeout)) { "Сценарий устарел — нет $what. ${seen()}" }

    /**
     * Жест и экран, к которому он ведёт. Жест, начатый, пока закрывается панель или доезжает
     * вкладка, приложение не принимает, поэтому повторяем его, пока экран не покажется.
     */
    private fun MacrobenchmarkScope.reach(mark: BySelector, gesture: () -> Unit) {
        repeat(3) {
            gesture()

            if (device.wait(Until.hasObject(mark), 3_000)) return
        }

        need(mark)
    }

    /**
     * Нажать, когда экран успокоился: касание посреди прокрутки её останавливает, а не нажимает.
     * Список после прокрутки ещё и пересобирается, и найденное успевает устареть — ищем заново.
     */
    private fun MacrobenchmarkScope.tap(what: BySelector) {
        device.waitForIdle()

        repeat(2) {
            try {
                return need(what).click()
            } catch (_: StaleObjectException) {
            }
        }

        need(what).click()
    }

    /** Подписи с экрана — в сообщение об ошибке: по ним видно, где сценарий разошёлся с приложением. */
    private fun MacrobenchmarkScope.seen() = "На экране: " + device.findObjects(By.text(Pattern.compile(".+")))
        .mapNotNull { runCatching { it.text }.getOrNull() }.joinToString(" | ")
}

/** Подпись кружка замера, пока замер идёт. */
private val CHECKING = Pattern.compile("Checking.*")

private val NODES = listOf("🇩🇪 Frankfurt", "🇳🇱 Amsterdam", "🇫🇮 Helsinki", "🇺🇸 New York")
    .flatMap { city -> (1..3).map { "$city $it" } }

/** A node's name on screen: the app wraps names in direction isolates (`String.isolated()`), not everywhere. */
private fun named(name: String): BySelector =
    By.text(Pattern.compile("⁨?" + Pattern.quote(name) + "⁩?"))

/**
 * Проверочная подписка. Узлы типа `direct`: ни чужих адресов, ни секретов, а VPN и замер
 * задержек на них работают по-настоящему. Групп несколько, узлов больше экрана — как у
 * настоящего провайдера.
 */
private val CONFIG = buildString {
    val nodes = NODES.joinToString { "\"$it\"" }

    appendLine("mode: rule")
    appendLine("log-level: warning")
    appendLine("dns: {enable: true, enhanced-mode: fake-ip, nameserver: [1.1.1.1, 8.8.8.8]}")
    appendLine("proxies:")
    NODES.forEach { appendLine("  - {name: \"$it\", type: direct}") }
    appendLine("proxy-groups:")
    appendLine("  - {name: Main, type: select, proxies: [Auto, $nodes]}")
    appendLine("  - {name: Auto, type: url-test, url: \"https://www.gstatic.com/generate_204\", interval: 300, proxies: [$nodes]}")
    appendLine("  - {name: Streaming, type: select, proxies: [Main, $nodes]}")
    appendLine("rules:")
    appendLine("  - MATCH,Main")
}

/**
 * Адрес устройства в его сети. По 127.0.0.1 ядро подписку не скачает: свои запросы оно
 * привязывает к сетевому интерфейсу. Пакетов этот сокет не шлёт — система лишь выбирает,
 * с какого адреса пошла бы наружу.
 */
private fun ownAddress() = DatagramSocket().use {
    it.connect(InetAddress.getByName("192.0.2.1"), 9)
    it.localAddress.hostAddress
}

/**
 * Раздаёт подписку приложению с этого же устройства. Заголовки — те, по которым главная
 * рисует карточки провайдера: объявление, срок, трафик, поддержка.
 */
private fun serveSubscription(): ServerSocket {
    val body = CONFIG.toByteArray()
    val head = buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: text/yaml; charset=utf-8\r\n")
        append("Content-Length: ${body.size}\r\n")
        append("Connection: close\r\n")
        append("profile-title: Baseline\r\n")
        append("announce: Test subscription for the baseline profile\r\n")
        append("support-url: https://example.com/\r\n")
        append("subscription-userinfo: upload=0; download=15000000000; total=500000000000; expire=1900000000\r\n")
        append("\r\n")
    }.toByteArray()

    val server = ServerSocket(0)

    thread(isDaemon = true) {
        while (true) {
            val client = runCatching { server.accept() }.getOrNull() ?: break

            runCatching {
                client.use {
                    val request = it.getInputStream().bufferedReader()
                    while (!request.readLine().isNullOrEmpty()) Unit

                    it.getOutputStream().run { write(head); write(body); flush() }
                }
            }
        }
    }

    return server
}
