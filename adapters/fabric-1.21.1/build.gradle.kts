plugins {
    id("fabric-loom") version "1.8-SNAPSHOT"
}

group = "com.mciot"
version = project.findProperty("mod_version") ?: "0.0.4"

base {
    archivesName.set("mciot-fabric-1.21.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.9")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.105.0+1.21.1")

    implementation(project(":mciot-core"))
}

sourceSets {
    main {
        java {
            srcDir("../mc-1.21.1/src/main/java")
            srcDir("../mc-1.20.1/src/main/java")
            include("**/FabricEntryV1211.java", "**/IoTTransceiverBlockV1211.java", "**/McWorldBridgeV1201.java", "**/IoTConfigScreenV1201.java")
        }
        resources {
            srcDir("../../shared-resources")
        }
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":mciot-core").sourceSets.main.get().output)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}
