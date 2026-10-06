plugins {
    id("com.android.library")
}

dependencies {

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
