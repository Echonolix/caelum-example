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
    id("net.echonolix.slang-gradle-plugin") version "0.0.1"
    id("net.echonolix.caelum-struct") version "1.0-SNAPSHOT"
    id("dev.luna5ama.jar-optimizer") version "1.2.2"
    id("me.champeau.jmh") version "0.7.3"
}

jmh {
    jvmArgs.set(listOf(
        "-Dfile.encoding=UTF-8",
        "--enable-native-access=ALL-UNNAMED",
        "-Dorg.lwjgl.util.NoChecks=true",
        "-Dorg.lwjgl.util.NoFunctionChecks=true",
        "-Dorg.lwjgl.util.NoHashChecks=true",
    ))
    javaToolchains {
        jvm.set(launcherFor {
            languageVersion.set(JavaLanguageVersion.of(24))
        }.get().executablePath.asFile.absolutePath)
    }
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:1.37")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    implementation("net.echonolix:caelum-core:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-vulkan:1.0-SNAPSHOT")
    implementation("net.echonolix:caelum-glfw-vulkan:1.0-SNAPSHOT")

    val lwjglVersion = "3.3.6"
    val lwjglNatives = "natives-windows"
    jmhImplementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    jmhImplementation("org.lwjgl", "lwjgl")
    jmhImplementation("org.lwjgl", "lwjgl-vulkan")
    jmhImplementation("org.lwjgl", "lwjgl-glfw")
    jmhRuntimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    jmhRuntimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.contracts.ExperimentalContracts")
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}

slang {
    compilerOptions {
        debugLogging.set(true)
        extraOptions.add("-fvk-use-entrypoint-name")
    }
}

val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)

    manifest {
        attributes(
            "Main-Class" to "net.echonolix.example.VkTestKt",
        )
    }
}

project.afterEvaluate {
    fatJar.configure {
        from(configurations.runtimeClasspath.get().elements.get().map { fileSystemLocation ->
            fileSystemLocation.asFile.let {
                if (it.isDirectory || !it.exists()) it else this.project.zipTree(it)
            }
        })
    }
}

val optimizeFatJar = jarOptimizer.register(fatJar, "net.echonolix.example")

artifacts {
    archives(optimizeFatJar)
}