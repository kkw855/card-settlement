plugins {
  // 버전만 고정하고, 적용은 앱 모듈(online-api, batch)에서 한다
  alias(libs.plugins.spring.boot) apply false
}

subprojects {
  apply(plugin = "java-library")

  group = "io.github.kkw855.settlement"
  version = "0.1.0"

  extensions.configure<JavaPluginExtension> {
    toolchain {
      languageVersion = JavaLanguageVersion.of(21)
    }
  }

  dependencies {
    "implementation"(platform(rootProject.libs.spring.boot.dependencies))

    "testImplementation"(rootProject.libs.junit.jupiter)
    "testImplementation"(rootProject.libs.assertj.core)
    "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
  }

  tasks.withType<JavaCompile>().configureEach {
    // 메서드 파라미터 이름을 바이트코드에 남긴다 (MyBatis #{name}, Spring 파라미터 바인딩)
    options.compilerArgs.add("-parameters")
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}
