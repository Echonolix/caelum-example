allprojects {
    group = "net.echonolix"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    kotlin("jvm")
    id("net.echonolix.slang-gradle-plugin")
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-windows"

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl", "lwjgl", lwjglVersion)
    implementation("org.lwjgl", "lwjgl-shaderc", lwjglVersion)
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)

    implementation("net.echonolix:caelum-core:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-vulkan:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-glfw-vulkan:1.0-SNAPSHOT")
}

slang {
    compilerOptions {
        debug.set(true)
    }
}

java {
    withSourcesJar()
}

allprojects {
    kotlin {
        compilerOptions {
            optIn.add("kotlin.contracts.ExperimentalContracts")
            freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
        }
    }
}