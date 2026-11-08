plugins {
    java
}

group = "com.mciot"
version = "1.0.0"

java {
    // 使用 Java 17 字节码，保证从 MC 1.20.1 (Java 17) 到 MC 1.21+ / 26.1 (Java 21/25) 100% 二进制兼容。
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
