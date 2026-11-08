plugins {
    id("net.neoforged.moddev") version "2.0.74"
}

group = "com.mciot"
version = project.findProperty("mod_version") ?: "0.0.4"

base {
    archivesName.set("mciot-neoforge-1.21.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = "21.1.72"
    mods {
        create("mciot") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":mciot-core"))
}

sourceSets {
    main {
        java {
            srcDir("../mc-1.21.1/src/main/java")
            srcDir("../mc-1.20.1/src/main/java")
            include("**/NeoForgeEntryV1211.java", "**/IoTTransceiverBlockV1211.java", "**/McWorldBridgeV1201.java")
        }
        resources {
            srcDir("../../shared-resources")
        }
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":mciot-core").sourceSets.main.get().output)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
