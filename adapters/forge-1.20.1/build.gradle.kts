plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.74"
}

group = "com.mciot"
version = project.findProperty("mod_version") ?: "1.0.0"

base {
    archivesName.set("mciot-forge-1.20.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

legacyForge {
    version = "1.20.1-47.3.0"
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
            srcDir("../mc-1.20.1/src/main/java")
            exclude("**/FabricEntryV1201.java")
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
}
