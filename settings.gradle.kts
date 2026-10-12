rootProject.name = "ducker"

include(":app")
include(":core")
include(":service")
include(":common")
include(":kaidl")
include(":baselineprofile")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
