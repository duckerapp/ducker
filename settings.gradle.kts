rootProject.name = "ClashMetaForAndroid"

include(":app")
include(":core")
include(":service")
include(":common")
include(":baselineprofile")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
