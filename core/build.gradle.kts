plugins {
    id("com.android.library")
    id("kotlinx-serialization")
}

/**
 * Сборка ядра своими руками (D-84).
 *
 * Раньше этим занимался плагин `golang-android`. Он вышел один раз, больше не обновлялся
 * и держится за классы AGP, которых в девятой ветке нет вовсе, — то есть однажды
 * заблокировал бы нас независимо от Compose. Делает он немного: ставит переменные
 * окружения кросс-компиляции и зовёт `go build`. Это и воспроизведено ниже.
 */

/** Как ABI Android называется в Go и в тулчейне NDK. */
data class GoAbi(val arch: String, val triple: String, val arm: String? = null)

val goAbis = mapOf(
    "arm64-v8a" to GoAbi("arm64", "aarch64-linux-android"),
    "armeabi-v7a" to GoAbi("arm", "armv7a-linux-androideabi", arm = "7"),
    "x86" to GoAbi("386", "i686-linux-android"),
    "x86_64" to GoAbi("amd64", "x86_64-linux-android"),
)

/** Which of them this build compiles — decided in the root script. */
val abis: List<String> by rootProject.extra

/** Главный модуль сборки — `foss`; пакет `cfa/native` приходит в него через `replace`. */
val goModuleDir = file("src/foss/golang")
val golangSource = file("src/main/golang/native")
val goPackage = "cfa/native"
val goTags = listOf("foss", "with_gvisor", "cmfa")

/** The pinned, patched Go (D-330): its version and patches are files of this directory. */
val goToolchain = file("toolchain")

// Тег `debug` ядру не ставим никогда. Для него это не «побольше логов», а другое
// поведение: в библиотеке sing он включает проверку утечек, и Buffer.Leak() вместо
// возврата буфера в пул вызывает panic("leaking buffer") — ядро падало через несколько
// секунд работы, на первой же проверке задержки узла (F-48).
val goOutputBase = layout.buildDirectory.dir("outputs/golang")

/** Имя задачи сборки ядра: его же ждут CMake, дымовой тест и docs/CORE-UPDATE.md. */
fun golangTaskName(variant: String, abi: String) =
    "externalGolangBuild" + variant.replaceFirstChar { it.uppercase() } +
        abi.split("-", "_").joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

android {
    productFlavors {
        all {
            externalNativeBuild {
                cmake {
                    arguments("-DGO_SOURCE:STRING=${golangSource}")
                    arguments("-DGO_OUTPUT:STRING=${goOutputBase.get().asFile}")
                    arguments("-DFLAVOR_NAME:STRING=$name")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    // Собранное ядро надо не только дать линковщику, но и положить в apk: `libbridge.so`
    // слинкован с `libclash.so` и без него не загрузится. Раньше это делал плагин;
    // теперь каталог сборки подкладывается в jniLibs варианта руками (D-84).
    //
    // Раскладка совпадает с той, что ждёт jniLibs: <вариант>/<abi>/libclash.so.
    sourceSets {
        // Флейвор у нас один (`meta`), поэтому набор исходников типа сборки и есть набор
        // варианта: `debug` — это `metaDebug`. Объединённых наборов вида «metaDebug»
        // в AGP 9 не существует, а варианты в DSL недоступны вовсе.
        mapOf("debug" to "metaDebug", "release" to "metaRelease").forEach { (buildType, variant) ->
            getByName(buildType).jniLibs.srcDir(
                layout.buildDirectory.dir("outputs/golang/$variant").get().asFile
            )
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

// Компилятор для cgo берём у NDK. Путь спрашиваем один раз и снаружи задач: внутри
// регистрации задачи обращение к `android.sdkComponents` заставляет AGP собрать свою
// внутреннюю реализацию раньше времени и падает.
val ndkDirectory = androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath }
val androidMinSdk = 29

androidComponents.onVariants { variant ->
    val cmakeName = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"

    goAbis.filterKeys { it in abis }.forEach { (abi, spec) ->
        val taskName = golangTaskName(variant.name, abi)
        val outputDir = goOutputBase.map { it.dir(variant.name).dir(abi) }

        val build = tasks.register<Exec>(taskName) {
            group = "build"
            description = "Собирает libclash.so для $abi (${variant.name})"

            workingDir = goModuleDir

            // Профильная оптимизация (D-79): файла нет — флаг не ставится.
            val pgoProfile = file("src/main/golang/native/default.pgo")

            // Сборка для съёма профиля поднимает pprof на петле. Отдельный тег, а не
            // готовый `debug`: тот роняет ядро проверкой утечек (F-48).
            val tags = if (project.hasProperty("pgo")) goTags + "pgo" else goTags

            val output = outputDir.get().asFile

            commandLine(
                buildList {
                    add(File(goToolchain, "go.sh").absolutePath)
                    add("build")
                    add("-buildmode"); add("c-shared")
                    add("-trimpath")
                    add("-o"); add(File(output, "libclash.so").absolutePath)
                    add("-tags"); add(tags.joinToString(","))
                    if (!project.hasProperty("pgo") && pgoProfile.isFile) {
                        add("-pgo=${pgoProfile.absolutePath}")
                    }
                    add(goPackage)
                }
            )

            environment("GOOS", "android")
            environment("GOARCH", spec.arch)
            environment("CGO_ENABLED", "1")
            spec.arm?.let { environment("GOARM", it) }

            doFirst {
                output.mkdirs()

                val host = if (System.getProperty("os.name").startsWith("Mac")) "darwin-x86_64"
                else "linux-x86_64"

                environment(
                    "CC",
                    "${ndkDirectory.get()}/toolchains/llvm/prebuilt/$host/bin/" +
                        "${spec.triple}$androidMinSdk-clang",
                )
            }

            // Без явных входов смена пина ядра или uTLS в go.mod/go.sum не считается
            // изменением, задача остаётся UP-TO-DATE и .so молча остаётся старым.
            // Проверено: после добавления replace для utls сборка прошла за 5 секунд,
            // а `go version -m libclash.so` показывал прежнюю версию модуля.
            inputs.dir(golangSource)
            // Своя копия sing-mux (D-105) и подмодуль ядра — тоже исходники .so. Без них
            // правка только в мультиплексе не пересобирала ядро: 0.4.6–0.4.8 ушли без двух
            // строк журнала, добавленных после первой сборки (D-110).
            inputs.dir(file("src/foss/golang/sing-mux"))
            inputs.dir(file("src/foss/golang/clash"))
            // uTLS — тоже подмодуль с 27.09.2026 (D-132).
            inputs.dir(file("src/foss/golang/utls"))
            inputs.file(file("src/foss/golang/go.mod"))
            inputs.file(file("src/foss/golang/go.sum"))
            inputs.file(file("src/main/golang/go.mod"))
            inputs.file(file("src/main/golang/go.sum"))
            // A new Go version or patch must rebuild the core as well.
            inputs.dir(goToolchain)
            if (pgoProfile.isFile) inputs.file(pgoProfile)

            outputs.dir(outputDir)
        }

        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        tasks.configureEach {
            // CMake линкуется с собранным ядром…
            if (name.startsWith("buildCMake$cmakeName[$abi]")) {
                dependsOn(build)
            }

            // …а упаковщик берёт его же файлом в apk. Обоим нужна явная зависимость:
            // без неё Gradle законно ругается, что задача читает чужой вывод вслепую.
            if (name == "merge${variantName}JniLibFolders") {
                dependsOn(build)
            }
        }
    }
}
