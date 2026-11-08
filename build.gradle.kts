plugins {
    id("java-library")
    `maven-publish`
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.github.DRAGNIL68:Packer:d7bb7af0cf")
    compileOnly("io.papermc.paper:paper-api:26.3.build.243-stable")
    // Optional Kokonut-Core utility menu (/메뉴2).
    compileOnly(files(providers.gradleProperty("kokonutCoreJar").orElse("../Kokonut_Core/build/libs/Kokonut-Core-cbt-1.0.jar")))
    // Optional Kokonut-Mail mailbox (main menu 우편함 button).
    compileOnly(files(providers.gradleProperty("kokonutMailJar").orElse("../Kokonut_Mail/build/libs/Kokonut-Mail-cbt-1.0.jar")))
    // Optional BetterHud (Kokonut_HUD) shader integration; resolved from the server at runtime.
    compileOnly(files(providers.gradleProperty("betterHudApiJar").orElse("../Kokonut_HUD/api/build/libs/betterhud-api-cbt-1.0.jar")))
}

val collectPluginArtifact = tasks.register<Copy>("collectPluginArtifact") {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../plugin"))
}
tasks.jar { finalizedBy(collectPluginArtifact) }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks {
    runServer {
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
}
