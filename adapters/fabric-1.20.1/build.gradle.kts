plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
}

group = "com.mciot"
version = project.findProperty("mod_version") ?: "1.0.0"

base {
    archivesName.set("mciot-fabric-1.20.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    minecraft("com.mojang:minecraft:1.20.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.9")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1")

    implementation(project(":mciot-core"))
}

sourceSets {
    main {
        java {
            srcDir("../mc-1.20.1/src/main/java")
            exclude("**/ForgeEntryV1201.java")
        }
        resources {
            srcDir("../../shared-resources")
        }
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":mciot-core").sourceSets.main.get().output)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}
