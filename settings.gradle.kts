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

// 按需独立挂载单个目标适配器，彻底隔离 Java 17 / Java 21 以及不同版本 Loom / ModDevGradle 的插件冲突！
val targetAdapter = providers.gradleProperty("targetAdapter").orNull
if (!targetAdapter.isNullOrBlank()) {
    include(":adapters:$targetAdapter")
}
