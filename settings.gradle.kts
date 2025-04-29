pluginManagement {
//    includeBuild("../ktgen")

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
    }
}

//includeBuild("../ktgen") {
//    dependencySubstitution {
//        substitute(module("net.echonolix:ktgen-api")).using(project(":api"))
//        substitute(module("net.echonolix:ktgen-runtime")).using(project(":runtime"))
//    }
//}

includeBuild("../caelum") {
    dependencySubstitution {
        substitute(module("net.echonolix:caelum-core")).using(project(":caelum-core"))
        substitute(module("net.echonolix:caelum-vulkan")).using(project(":caelum-vulkan"))
    }
}

rootProject.name = "vktest"