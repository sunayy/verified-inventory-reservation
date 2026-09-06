import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.4.13"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example.inventory"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

val repositoryRoot = layout.projectDirectory.dir("..").asFile
val nativeLibraryDirectory = repositoryRoot.resolve("verified/.lake/build/lib")

val buildNativeReservation by tasks.registering(Exec::class) {
    workingDir = repositoryRoot
    commandLine("./scripts/build-lean-native.sh")
}

val generateQuintTraces by tasks.registering(Exec::class) {
    workingDir = repositoryRoot
    commandLine("npm", "run", "generate:traces")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(buildNativeReservation)
    jvmArgs(
        "-Djava.library.path=${nativeLibraryDirectory.absolutePath}",
        "--enable-native-access=ALL-UNNAMED",
    )
    exclude("**/MbtReplayTest.class")
}

tasks.bootRun {
    dependsOn(buildNativeReservation)
    jvmArgs("-Djava.library.path=${nativeLibraryDirectory.absolutePath}", "--enable-native-access=ALL-UNNAMED")
}

tasks.register<Test>("testMbt") {
    description = "Generates Quint ITF traces and replays every action against Kotlin."
    group = "verification"
    dependsOn(buildNativeReservation, generateQuintTraces)
    inputs.dir(repositoryRoot.resolve("spec/traces"))
    outputs.upToDateWhen { false }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/MbtReplayTest.class")
    systemProperty("mbt.traceDir", repositoryRoot.resolve("spec/traces").absolutePath)
    jvmArgs(
        "-Djava.library.path=${nativeLibraryDirectory.absolutePath}",
        "--enable-native-access=ALL-UNNAMED",
    )
}

tasks.register<Test>("testPbt") {
    description = "Runs the Kotest property suite with its 1,000-iteration properties."
    group = "verification"
    dependsOn(buildNativeReservation)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    include("**/ReservationPropertiesTest.class")
    jvmArgs(
        "-Djava.library.path=${nativeLibraryDirectory.absolutePath}",
        "--enable-native-access=ALL-UNNAMED",
    )
}
