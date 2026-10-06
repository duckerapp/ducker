plugins {
    id("kotlinx-serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))

    ksp(libs.kaidl.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kaidl.runtime)
    implementation(libs.rikkax.multiprocess)
}

// Прежде здесь вручную подкладывались сгенерированные KSP исходники: старая пара
// AGP и KSP сама этого не делала. Теперь делает, а старый способ (`libraryVariants`)
// в AGP 9 удалён вместе со всем прежним Variant API (D-84).

// ducker: проверка арифметики маршрутов (D-56) — только на JVM, в APK не попадает
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
