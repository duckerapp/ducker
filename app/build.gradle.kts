import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    // Плагин Kotlin не нужен: с AGP 9 поддержка Kotlin встроена в сам Android-плагин (D-84).
    id("com.android.application")
    // Compose UI (D-3).
    id("org.jetbrains.kotlin.plugin.compose")
    // ducker: baseline profile (этап 8.1)
    id("androidx.baselineprofile")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":common"))

    // Кладёт baseline profile в ART на первом запуске; на Android 12+ это делает система,
    // но у нас minSdk 29.
    implementation(libs.androidx.profileinstaller)
    // Редактор конфига (D-124): рисует только видимые строки, большие конфиги не тормозят.
    implementation(libs.sora.editor)
    // Parse YAML for config export, including flow mappings, aliases, and quoted keys.
    implementation(libs.snakeyaml)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    // Только ради AppCompatActivity: до Android 13 выбор языка приложения
    // работает лишь через неё (D-49). Экранов на View у нас больше нет.
    implementation(libs.androidx.appcompat)
    // Сканер QR: распознаёт zxing-cpp, камера — CameraX. Без ML Kit: тот шлёт Google
    // статистику использования (D-88).
    implementation(libs.zxing.cpp)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // QR подписки для «Поделиться»: один класс на Java, MIT, без зависимостей.
    implementation(libs.qrcodegen)

    // ducker: свой интерфейс. Версии держит BOM.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ducker: проверка чистки журнала (инвариант 4) — только на JVM
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

// ducker: панель zashboard лежит в ресурсах приложения, а не качается с сети во время
// работы (инвариант 3, D-33). Версия закреплена, чтобы сборка была воспроизводимой.
val zashboardVersion = "v3.29.1"
val zashboardDir = file("src/main/assets/zashboard")

task("downloadZashboard") {
    val marker = File(zashboardDir, ".version")

    outputs.upToDateWhen { marker.isFile && marker.readText() == zashboardVersion }

    doLast {
        val archive = File(temporaryDir, "zashboard.zip")

        URL(
            "https://github.com/Zephyruso/zashboard/releases/download/" +
                "$zashboardVersion/dist-no-fonts.zip"
        ).openStream().use { input ->
            Files.copy(input, archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        delete(zashboardDir)

        copy {
            // В архиве всё лежит внутри `dist/`, этот уровень нам не нужен.
            from(zipTree(archive)) { eachFile { path = path.removePrefix("dist/") } }
            into(zashboardDir)
            includeEmptyDirs = false
        }

        marker.writeText(zashboardVersion)

        println("zashboard $zashboardVersion unpacked to $zashboardDir")
    }
}

val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        // ducker: ASN.mmdb (12,1 МБ) и BundleMRS.7z (9,2 МБ) не кладём в APK. Первый нужен
        // только правилам IP-ASN, второй — провайдерам с ключом path-in-bundle; ни того,
        // ни другого нет ни в нашем шаблоне, ни в конфигах панели. Чужому профилю, которому
        // они понадобятся, ядро скачает их само (component/geodata/init.go).
)

task("downloadGeoFiles") {
    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")

            // Уже лежит — не качаем. Задача висит на каждом `assemble`, а базы весят
            // 12 МБ: без этой проверки любая сборка упиралась в сеть, и отдача 504
            // от GitHub останавливала работу целиком. Обновить — `clean` или удалить файл.
            if (outputPath.length() > 1_000_000) {
                println("$outputFileName is already in place, skipping download")

                return@forEach
            }

            val url = URL(downloadUrl)
            outputPath.parentFile.mkdirs()
            url.openStream().use { input ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]
    val downloadZashboardTask = tasks["downloadZashboard"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
            it.dependsOn(downloadZashboardTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    // Удаляем только скачанное, а не весь каталог assets: рядом лежит наш собственный
    // ducker/template.yaml — он в репозитории, а не качается, и `clean` сносил его
    // вместе с geo-базами. Восстанавливать приходилось из истории git.
    delete(geoFilesUrls.values.map { file("$geoFilesDownloadDir/$it") })
    delete(zashboardDir)
}
