plugins {
    id("fabric-loom") version "1.8-SNAPSHOT"
}

group = "com.mciot"
version = project.findProperty("mod_version") ?: "0.0.4"

base {
    archivesName.set("mciot-fabric-26.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    val mcVer = (project.findProperty("mc_26_version") ?: "1.21.4").toString()
    val fapiVer = (project.findProperty("fapi_26_version") ?: "0.114.0+1.21.4").toString()
    minecraft("com.mojang:minecraft:$mcVer")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fapiVer")

    implementation(project(":mciot-core"))
}

sourceSets {
    main {
        java {
            srcDir("../mc-26.1/src/main/java")
            srcDir("../mc-1.21.1/src/main/java")
            srcDir("../mc-1.20.1/src/main/java")
            include("**/FabricEntryV2601.java", "**/IoTTransceiverBlockV1211.java", "**/McWorldBridgeV1201.java")
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
