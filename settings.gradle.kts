pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "mc-iot-platform"

include(":mciot-core")
include(":adapters:fabric-1.20.1")
include(":adapters:forge-1.20.1")
include(":adapters:fabric-1.21.1")
include(":adapters:neoforge-1.21.1")
include(":adapters:fabric-26.1")
