plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "ru.mdview"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.jsoup:jsoup:1.17.2")

    testImplementation(kotlin("test"))
}

// Компилятор и запуск должны видеть модуль jcef, поэтому тулчейк не настраиваем:
// берётся тот JDK, на котором работает сам Gradle (см. org.gradle.java.home в gradle.properties).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xadd-modules=jcef")
    }
}

application {
    mainClass.set("ru.mdview.MainKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jcef")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-modules", "jcef")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
