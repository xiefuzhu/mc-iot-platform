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

// 95% 跨版本零依赖微内核（Java 17 编译，兼容 1.20.1 ~ 26.1 所有版本）
include(":mciot-core")

// 按需启用对应 Minecraft 版本与加载器的子模块进行独立极小化打包：
// include(":adapters:mc-1.20.1")
// include(":adapters:mc-1.21.1")
// include(":adapters:mc-26.1")
