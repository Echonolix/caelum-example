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
    id("dev.luna5ama.jar-optimizer") version "1.2.2"
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

val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
}

project.afterEvaluate {
    fatJar.configure {
        from(configurations.runtimeClasspath.get().elements.get().map { fileSystemLocation ->
            fileSystemLocation.asFile.let {
                if (it.isDirectory) it else this.project.zipTree(it)
            }
        })
    }
}

val optimizeFatJar = jarOptimizer.register(fatJar, "dev.luna5ama.echonolix")

artifacts {
    archives(optimizeFatJar)
}