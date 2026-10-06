// Модуль существует только ради генерации baseline profile (этап 8.1): он запускает
// приложение на подключённом устройстве, записывает, какие классы и методы нужны на
// старте, и кладёт результат в app/src/meta/generated/baselineProfiles. В APK не попадает.
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.github.kr328.clash.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    targetProjectPath = ":app"

    // Флейвор у приложения один (см. корневой build.gradle.kts), но объявить его нужно
    // и здесь, иначе плагину не с чем сопоставить вариант.
    flavorDimensions += "feature"
    productFlavors {
        create("meta") { dimension = "feature" }
    }
}

baselineProfile {
    // Устройствами Gradle не управляет. Саму задачу generate…BaselineProfile руками не запускать:
    // она ставит сборку на все подключённые устройства и стирает на них данные приложения (F-86).
    // Профиль снимает scripts/baseline-profile.sh — на одном названном эмуляторе.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
