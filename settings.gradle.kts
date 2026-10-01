rootProject.name = "card-settlement"

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    mavenCentral()
  }
}

include("codec", "core", "online-api", "batch", "simulator")
