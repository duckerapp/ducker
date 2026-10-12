// The generator of the Binder glue (D-268): a KSP processor that runs inside the build of
// `:service` and never reaches the APK. Where it comes from and what it writes — README.md.
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
}
