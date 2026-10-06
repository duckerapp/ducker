package com.github.kr328.clash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom

/**
 * Раздел «Расширенно»: панель zashboard (D-4, D-33).
 *
 * Панель отдаёт само ядро: при открытии экрана мы пишем в **сессионный** оверрайд адрес
 * `127.0.0.1:<свободный порт>` и случайный секрет, ядро поднимает свой REST API и раздаёт
 * статические файлы по `/ui/`. При выходе оверрайд очищается, и API закрывается.
 *
 * Почему именно так, а не WebView поверх файлов приложения: страница, отданная ядром,
 * лежит на том же адресе, что и API, — значит не нужны ни разрешения CORS, ни разрешение
 * смешанного содержимого для веб-сокетов, из которых панель берёт трафик и журнал.
 */
class DashboardActivity : ComponentActivity() {
    private var port = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            // Панель — локальная страница, и уходить с неё внутри нашего окна незачем.
            // Любую внешнюю ссылку отдаём браузеру: в адресе страницы лежит секрет
            // доступа к API ядра, и уносить его на чужой сайт заголовком Referer
            // не нужно.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (request.url.host == "127.0.0.1") return false

                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }

                    return true
                }
            }
        }

        // Приложение рисуется во весь экран, а панель об этом не знает: без отступов её
        // шапка уезжает под строку состояния. Отступ вешаем на контейнер, а не на сам
        // WebView: страницу внутри него собственный padding не сдвигает.
        val frame = FrameLayout(this).apply { addView(web) }

        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)

            insets
        }

        setContentView(frame)

        // Внутри панели много переходов; системная «назад» должна ходить по ним,
        // а не закрывать экран с первого нажатия.
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finish()
        }

        lifecycleScope.launch {
            // Панель живёт внутри ядра, поэтому без поднятого туннеля открывать нечего. Спрашиваем
            // службу прямо: в свежем процессе `:dashboard` флаг Remote.broadcasts ещё не заполнен.
            if (withContext(Dispatchers.IO) { !StatusClient(this@DashboardActivity).status().first }) {
                toast(R.string.dashboard_needs_connection)

                return@launch finish()
            }

            val secret = randomSecret()

            port = withContext(Dispatchers.IO) { freePort() }

            withContext(Dispatchers.IO) { unpackDashboard() }

            open(port, secret)

            if (!withContext(Dispatchers.IO) { awaitController(port) }) {
                toast(R.string.dashboard_failed)

                return@launch finish()
            }

            web.loadUrl("http://127.0.0.1:$port/ui/?hostname=127.0.0.1&port=$port&secret=$secret")
        }
    }

    /**
     * Закрывать API нужно даже если экран уже уничтожен, поэтому работа идёт вне
     * жизненного цикла активности. Сессионный оверрайд всё равно очищается при перезапуске
     * туннеля, но ждать перезапуска ради закрытия порта неправильно.
     *
     * Панель живёт в своём процессе `:dashboard` (D-141), и по выходе он завершается: WebView,
     * запущенный однажды, держит свой процесс отрисовки и библиотеку Chromium до конца процесса
     * приложения, `destroy()` этого не отдаёт. В общем процессе после закрытия панели оставалось
     * ~190 МБ. При повороте экран пересоздаётся — тогда процесс нужен дальше.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        super.onDestroy()

        val exit = isFinishing

        GlobalScope.launch {
            if (port != 0) withTimeoutOrNull(CLEANUP_MS) {
                // К этому моменту процесс уже невидим, и Remote отвязался от службы (onStop), а
                // главного экрана, который привязал бы её снова, в этом процессе нет.
                Remote.service.bind()

                runCatching {
                    withClash {
                        val override = queryOverride(Clash.OverrideSlot.Session)

                        override.externalController = null
                        override.secret = null

                        patchOverride(Clash.OverrideSlot.Session, override)
                    }
                }
            }

            if (exit) Process.killProcess(Process.myPid())
        }
    }

    private suspend fun open(port: Int, secret: String) {
        runCatching {
            withClash {
                val override = queryOverride(Clash.OverrideSlot.Session)

                override.externalController = "127.0.0.1:$port"
                override.secret = secret

                // Запись оверрайда рассылает ACTION_OVERRIDE_CHANGED, по которому служба
                // перечитывает профиль, — на этом шаге ядро и поднимает API.
                patchOverride(Clash.OverrideSlot.Session, override)
            }
        }
    }

    /** Перечитывание профиля занимает заметное время, поэтому ждём ответа, а не гадаем. */
    private fun awaitController(port: Int): Boolean {
        repeat(ATTEMPTS) {
            val answered = runCatching {
                val connection = URL("http://127.0.0.1:$port/").openConnection()
                        as HttpURLConnection

                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT

                try {
                    // Без секрета ядро отвечает 401 — для проверки «порт жив» этого хватает.
                    connection.responseCode > 0
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)

            if (answered) return true

            Thread.sleep(STEP)
        }

        return false
    }

    /**
     * Файлы панели лежат в ресурсах приложения (инвариант 3), а ядро раздаёт их из своего
     * дома. Копируем один раз на версию приложения: ресурсы меняются только вместе с ней.
     */
    private fun unpackDashboard() {
        val target = filesDir.resolve("clash/dashboard")
        val marker = File(target, ".version")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

        if (marker.isFile && marker.readText() == version) return

        target.deleteRecursively()

        copyAssets("zashboard", target)

        marker.writeText(version)
    }

    private fun copyAssets(path: String, target: File) {
        val children = assets.list(path) ?: emptyArray()

        if (children.isEmpty()) {
            target.parentFile?.mkdirs()

            assets.open(path).use { input -> target.outputStream().use(input::copyTo) }

            return
        }

        target.mkdirs()

        for (child in children) {
            copyAssets("$path/$child", File(target, child))
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun randomSecret(): String {
        val bytes = ByteArray(16)

        SecureRandom().nextBytes(bytes)

        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toast(text: Int) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ATTEMPTS = 40
        private const val STEP = 150L
        private const val TIMEOUT = 1000
        private const val CLEANUP_MS = 3000L
    }
}
