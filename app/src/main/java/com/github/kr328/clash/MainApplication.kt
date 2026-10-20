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
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.disguised
import com.github.kr328.clash.util.extractGeoAsset
import java.io.File

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

            // Базы распаковывает только тот процесс, которому они нужны, — здесь живёт
            // ядро (D-77). Интерфейсу они не нужны вовсе, а стоили ему первого кадра
            // после каждого обновления: 12,7 МБ из сжатых ресурсов, в главном потоке.
            // Заодно ушла гонка: два процесса могли распаковывать разом, и второй
            // видел файл, который первый ещё дописывал.
            extractGeoFiles()

            sendServiceRecreated()
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
            extractGeoAsset(File(clashDir, name), updateDate) { assets.open(name) }
        }
    }

    // Иконки групп и логотипы провайдеры часто дают в SVG, а сам Coil его не читает.
    // Coil 3 by default reuses a cached image forever; the provider's Cache-Control decides when a
    // changed logo is fetched again (D-332).
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
            add(OkHttpNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() }))
        }
        .build()
}
