pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://www.jitpack.io") {
      content {
        // Only use JitPack for specific dependencies to avoid unnecessary checks
        includeGroup("io.github.abdallahmehiz")
        includeGroup("com.github.abdallahmehiz")
        includeGroup("com.github.K1rakishou")
        includeGroup("com.github.marlboro-advance")
        includeGroup("com.github.thegrizzlylabs")
        includeGroup("com.github.nanihadesuka")
        includeGroup("com.github.jeziellago")
      }
    }
    ivy {
      url = uri("https://github.com/estiaksoyeb/mpv-android/releases/download/")
      patternLayout {
        artifact("[revision]/[artifact]-[revision].[ext]")
      }
      metadataSources {
        artifact()
      }
      content {
        includeGroup("xyz.mpv.rex.libs")
      }
    }
  }
}

rootProject.name = "mpvEx"
include(":app")
