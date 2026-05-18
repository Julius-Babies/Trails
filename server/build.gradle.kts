
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.application)
}

group = "es.jvbabi.trails"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)

    implementation(libs.clikt)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.sqlite)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    implementation(libs.logback.classic)

    implementation(libs.bcrypt)
    implementation(libs.csv)
    implementation(libs.gson)

    implementation(libs.kotlinx.datetime)

    implementation(libs.authentikt)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
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

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.EC")
    }
}