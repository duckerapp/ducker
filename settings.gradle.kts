rootProject.name = "ducker"

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
