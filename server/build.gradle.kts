plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.application)
}

group = "es.jvbabi.trails"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

dependencies {
    implementation(projects.shared)

    // Ktor Server
    implementation(libs.server.ktor.server.core)
    implementation(libs.server.ktor.server.netty)
    implementation(libs.server.ktor.server.websockets)
    implementation(libs.server.ktor.server.content.negotiation)
    implementation(libs.server.ktor.server.auth.jwt)
    implementation(libs.server.ktor.server.call.logging)
    implementation(libs.server.ktor.serialization.kotlinx.json)

    // Ktor Client
    implementation(libs.server.ktor.client.core)
    implementation(libs.server.ktor.client.cio)
    implementation(libs.server.ktor.client.content.negotiation)

    // CLI
    implementation(libs.server.clikt)

    // Database
    implementation(libs.server.exposed.core)
    implementation(libs.server.exposed.dao)
    implementation(libs.server.exposed.jdbc)
    implementation(libs.server.exposed.kotlin.datetime)
    implementation(libs.server.sqlite)
    implementation(libs.server.postgres)

    // DI & Logging
    implementation(libs.server.koin.ktor)
    implementation(libs.server.koin.loggerSlf4j)
    implementation(libs.server.logback.classic)

    // Utilities
    implementation(libs.server.bcrypt)
    implementation(libs.server.csv)
    implementation(libs.server.gson)
    implementation(libs.server.kotlinx.datetime)

    // Auth - private GitHub package
    implementation(libs.server.authentikt)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.server.ktor.server.test.host)
}

application {
    mainClass.set("es.jvbabi.trails.MainKt")
}

tasks.register<Jar>("buildServerJar") {
    group = "build"
    description = "Assembles a fat JAR with all dependencies bundled."
    archiveBaseName.set(project.name)
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    }
}
