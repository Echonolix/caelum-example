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

dependencies {
    implementation("net.echonolix:caelum-core:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-vulkan:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-glfw-vulkan:1.0-SNAPSHOT")
}

java {
    withSourcesJar()
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xbackend-threads=0", "-Xcontext-parameters")
    }
}

slang {
    compilerOptions {
        debug.set(true)
        extraOptions.add("-fvk-use-entrypoint-name")
    }
}
