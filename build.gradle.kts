plugins {
    java
}

group = "pe.kydium"
version = "6.9.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Versión de Paper para compilar. Se puede cambiar al compilar:
//   gradle build -PpaperVersion=26.3.build.+
val paperVersion = (findProperty("paperVersion") as String?) ?: "26.2.build.+"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("AventuraMaker-${project.version}.jar")
}
