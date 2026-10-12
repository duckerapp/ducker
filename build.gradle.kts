@file:Suppress("UNUSED_VARIABLE")

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.plugins.BasePluginExtension
import java.io.File
import java.net.URI
import java.util.*

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath(libs.build.android)
        classpath(libs.build.kotlin.common)
        classpath(libs.build.kotlin.serialization)
        classpath(libs.build.ksp)
        classpath(libs.build.kotlin.compose)
        classpath(libs.build.baselineprofile)
    }
}

// A debug build goes to one device, so it gets one ABI instead of four: the Go core is
// compiled once and one APK is packaged. `-Pabi=x86_64` picks another one (an emulator),
// `-Pabi=x86,x86_64` several; Android Studio passes the ABI of the device it runs on.
// Everything else — release files, CI, a bare sync — builds all four.
// ponytail: "debug" is read from the requested task names; a mixed run builds all four.
val abis by extra(
    ((findProperty("abi") ?: findProperty("android.injected.build.abi")) as String?)?.split(",")
        ?: gradle.startParameter.taskNames
            .takeIf { names -> names.isNotEmpty() && names.all { "Debug" in it } }
            ?.let { listOf("arm64-v8a") }
        ?: listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
)

subprojects {
    repositories {
        mavenCentral()
        google()
    }

    // Модуль генерации baseline profile настраивает себя сам: он не приложение и не
    // библиотека, а com.android.test, и общая настройка ниже ему не подходит.
    // The same goes for `kaidl`, the generator of the Binder glue (D-268): a plain JVM module
    // that runs inside the build.
    if (name == "baselineprofile" || name == "kaidl") {
        return@subprojects
    }

    val isApp = name == "app"

    apply(plugin = if (isApp) "com.android.application" else "com.android.library")

    fun queryConfigProperty(key: String): Any? {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        } else {
            return null
        }
        return localProperties.getProperty(key)
    }

    // AGP 9 убрал `BaseExtension`, а у общего `CommonExtension` оставил только
    // свойства — блоков вида `defaultConfig { }` у него нет (D-84). Поэтому общее,
    // что живёт на свойствах, настраивается функцией ниже, а контейнеры (флейворы,
    // типы сборки, подписи) — в блоках своего типа: у приложения и библиотеки они
    // разные, и это правильно.
    fun CommonExtension.configureCommon() {
        // В AGP 9 и `buildConfig`, и `resValue` выключены по умолчанию: обе возможности
        // стоят лишнего времени сборки там, где ими не пользуются. Нам нужны обе.
        buildFeatures.buildConfig = true
        buildFeatures.resValues = true

        namespace = if (name == "app") "com.github.kr328.clash"
        else "com.github.kr328.clash.$name"

        // Собираем против 36: свежий Compose требует его, а поведение приложения задаёт
        // targetSdk, и он остаётся 35 (D-78). Новые правила Android 16 — отдельная
        // работа со своей проверкой на устройстве.
        compileSdk = 37
        ndkVersion = "29.0.14206865"

        defaultConfig.apply {
            minSdk = 29

            // The app's ABIs come from `splits` below: AGP rejects both settings together
            // unless a universal APK is built as well.
            if (!isApp) {
                ndk {
                    abiFilters += abis
                }
            }

            externalNativeBuild {
                cmake {
                    abiFilters(*abis.toTypedArray())
                }
            }
        }

        packaging.resources.excludes += "DebugProbesKt.bin"

        flavorDimensions += "feature"

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    // ducker: флейвор один. У CMFA их два, alpha и meta, но отличаются они только именем
    // и суффиксом, а Go-сборка из-за этого делается дважды на каждый ABI. Имя `meta`
    // сохранено: оно же задаёт имя каталога исходников Go в core.
    fun ProductFlavor.configureMeta() {
        // `isDefault` живёт не здесь, а у флейвора приложения и библиотеки по отдельности,
        // поэтому его ставят вызывающие.
        dimension = "feature"

        resValue("string", "launch_name", "@string/launch_name_meta")
        resValue("string", "application_name", "@string/application_name_meta")
    }

    if (isApp) {
        extensions.configure<ApplicationExtension> {
            configureCommon()

            defaultConfig {
                val customApplicationId = queryConfigProperty("custom.application.id") as? String?
                applicationId = customApplicationId.takeIf { it?.isNotBlank() == true } ?: "app.ducker.android"

                targetSdk = 35

                versionName = "0.4.32"
                versionCode = 36

                resValue("string", "release_name", "v$versionName")
                resValue("integer", "release_code", "$versionCode")

                // Где лежит update.json: GitHub, за ним зеркало на GitLab — приложение идёт по
                // списку. Проверить обновление без выкладки, с компьютера:
                // -Pducker.update.urls=http://127.0.0.1:8765/update.json и adb reverse tcp:8765 tcp:8765.
                // Last is jsDelivr's copy of the GitHub file: one more chance where both sites are
                // unreachable, though it can lag by up to 12 hours. Release files do not fit there.
                val updateUrls = providers.gradleProperty("ducker.update.urls").orNull
                    ?: ("https://raw.githubusercontent.com/duckerapp/ducker/main/update.json," +
                        "https://gitlab.com/duckerx/ducker/-/raw/main/update.json," +
                        "https://cdn.jsdelivr.net/gh/duckerapp/ducker@main/update.json")
                buildConfigField("String", "UPDATE_URLS", "\"$updateUrls\"")
            }

            productFlavors {
                create("meta") {
                    isDefault = true
                    configureMeta()
                }
            }

            // Только после объявления флейвора: его набор исходников до этого не существует.
            sourceSets.getByName("meta").java.srcDirs("src/foss/java")

            // Ключ подписи (D-82). Настройки берутся из `signing.properties` в корне; файла
            // нет — обе сборки подписываются отладочным ключом, и это нормально для машины,
            // где ключа быть не должно.
            signingConfigs {
                val settings = rootProject.file("signing.properties")

                if (settings.exists()) {
                    create("release") {
                        // Читаем через Reader в UTF-8, а не потоком: `Properties.load`
                        // от потока разбирает байты как ISO-8859-1, и путь с кириллицей
                        // («Документы») превращается в мусор — сборка падает с «keystore
                        // file not found» на строку, которая глазами выглядит правильной.
                        val prop = Properties().apply {
                            settings.reader(Charsets.UTF_8).use(this::load)
                        }

                        // Путь задаётся в настройках и ведёт НАРУЖУ репозитория: ключ в git
                        // не должен попасть ни при какой оплошности. Прежнее поведение —
                        // `release.keystore` рядом с корнем — осталось запасным вариантом.
                        storeFile = prop.getProperty("keystore.path")?.let(::File)
                            ?: rootProject.file("release.keystore")
                        storePassword = prop.getProperty("keystore.password")!!
                        keyAlias = prop.getProperty("key.alias")!!
                        keyPassword = prop.getProperty("key.password")!!
                    }
                }
            }

            buildTypes {
                named("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
                named("debug") {
                    versionNameSuffix = ".debug"

                    // Тем же ключом, что и релиз (D-82). Подпись — это удостоверение личности
                    // приложения: разные ключи значат разные приложения, и отладочная сборка
                    // не встала бы поверх релизной без удаления, унося с собой подписку
                    // и настройки. А ещё идентификатор устройства считается из подписи (D-31),
                    // и с двумя ключами один телефон занимал бы в панели два слота.
                    signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
                }
            }

            splits {
                abi {
                    isEnable = true
                    reset()
                    include(*abis.toTypedArray())
                }
            }
        }

        // Имя файла apk с номером версии: по нему видно, что именно раздали, и его же
        // ждёт скрипт релиза. Номер берём из настроек, а не повторяем строкой — иначе
        // после подъёма версии имя осталось бы прежним, и релиз собрался бы «мимо».
        extensions.configure<BasePluginExtension> {
            archivesName.set(
                "ducker-" + extensions.getByType<ApplicationExtension>().defaultConfig.versionName
            )
        }
    } else {
        extensions.configure<LibraryExtension> {
            configureCommon()

            defaultConfig {
                consumerProguardFiles("consumer-rules.pro")
            }

            productFlavors {
                create("meta") {
                    isDefault = true
                    configureMeta()
                }
            }

            // Только после объявления флейвора: его набор исходников до этого не существует.
            sourceSets.getByName("meta").java.srcDirs("src/foss/java")

            buildTypes {
                named("release") {
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
        }
    }
}

task("clean", type = Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL

    doLast {
        val sha256 = URI("$distributionUrl.sha256").toURL().openStream()
            .use { it.reader().readText().trim() }

        file("gradle/wrapper/gradle-wrapper.properties")
            .appendText("distributionSha256Sum=$sha256")
    }
}