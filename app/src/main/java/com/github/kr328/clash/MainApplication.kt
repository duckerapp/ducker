package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.mainActivityAlias
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.FlightRecorder
import com.github.kr328.clash.service.CrashRecorder
import com.github.kr328.clash.service.TunGuard.Companion.noticeTunnelLeftDown
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.recoverReplaced
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.disguised
import com.github.kr328.clash.util.extractGeoAsset
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Suppress("unused")
class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        CrashRecorder.install(this, currentProcessName)
        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName

        Log.d("Process $processName started")

        restoreLauncherIcon()

        // Панель zashboard — в своём процессе (D-141): ей нужна связь со службой, как интерфейсу,
        // но не то, что делает процесс службы.
        if (processName == packageName || processName == "$packageName:dashboard") {
            Remote.launch()
        } else {
            // Самописец службы (D-111): пишет всегда, чтобы сбой было видно в выгрузке
            // журнала и после того, как он случился.
            FlightRecorder.start(this)

            // A subscription's directory cut off mid-replacement by a death is put back before anything
            // of this process reads it.
            importedDir.recoverReplaced()

            // Базы распаковывает только тот процесс, которому они нужны, — здесь живёт
            // ядро (D-77). Интерфейсу они не нужны вовсе, а стоили ему первого кадра
            // после каждого обновления: 12,7 МБ из сжатых ресурсов, в главном потоке.
            // Заодно ушла гонка: два процесса могли распаковывать разом, и второй
            // видел файл, который первый ещё дописывал.
            extractGeoFiles()

            sendServiceRecreated()

            noticeTunnelLeftDown()
        }
    }

    /**
     * «Скрыть значок» убран (D-140): с Android 10 система всё равно ставит на место значка ярлык
     * «О приложении». У тех, кто успел скрыть, значок возвращается при первом запуске любого
     * процесса — после обновления это обычно служба, которую поднимает `RestartReceiver`.
     * При маскировке (D-147) значок ducker выключен намеренно, его место занял калькулятор.
     */
    private fun restoreLauncherIcon() {
        if (packageManager.getComponentEnabledSetting(mainActivityAlias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return
        if (disguised) return

        packageManager.setComponentEnabledSetting(
            mainActivityAlias, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP,
        )
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        for (name in listOf("geoip.metadb", "geosite.dat")) {
            // A full disk took this process down at every start until space was freed. The old database
            // stays in place, and the next start tries again.
            try {
                extractGeoAsset(File(clashDir, name), updateDate) { assets.open(name) }
            } catch (e: IOException) {
                Log.w("Extract $name: $e")
            }
        }
    }

    // Иконки групп и логотипы провайдеры часто дают в SVG, а сам Coil его не читает.
    // Coil 3 by default reuses a cached image forever; the provider's Cache-Control decides when a
    // changed logo is fetched again (D-332).
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
            add(OkHttpNetworkFetcherFactory(callFactory = { imageClient() }, cacheStrategy = { CacheControlCacheStrategy() }))
        }
        .build()

    /**
     * The provider sets every picture's address (D-45), so a picture's size and time are bounded: a 48 MB SVG logo
     * took the main process's heap to its limit while it was read (R2). An application interceptor sees the body after
     * gzip is undone, so a small compressed file cannot grow past the limit either.
     */
    private fun imageClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(IMAGE_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addInterceptor response

            if (body.contentLength() > IMAGE_MAX_BYTES) {
                response.close()
                throw IOException("image over $IMAGE_MAX_BYTES bytes")
            }

            val capped = object : ForwardingSource(body.source()) {
                private var read = 0L

                override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, byteCount).also {
                    if (it > 0) read += it
                    if (read > IMAGE_MAX_BYTES) throw IOException("image over $IMAGE_MAX_BYTES bytes")
                }
            }

            response.newBuilder().body(capped.buffer().asResponseBody(body.contentType(), body.contentLength())).build()
        }
        .build()
}

/** A logo or a group icon is a few hundred kilobytes; a 625 KB one was drawn fine (F-370). */
private const val IMAGE_MAX_BYTES = 2L * 1024 * 1024
private const val IMAGE_TIMEOUT_S = 30L
