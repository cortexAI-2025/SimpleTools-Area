pluginManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") // alias officiel de Maven Central
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") // alias officiel de Maven Central
    }
}
rootProject.name = "SimpleToolsArea"
include(":app")
