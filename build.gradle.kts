private val kotlinVersion = "1.9.22"

plugins {
  kotlin("jvm") version "1.9.22"
}

sourceSets {
  val test by getting {
    java.srcDir("src/test/kotlin")
  }
}

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  testImplementation("androidx.compose.runtime:runtime:1.7.2")
  testImplementation("androidx.compose.compiler:compiler-hosted:1.5.10")
  testImplementation(kotlin("compiler", kotlinVersion))
  testImplementation("junit:junit:4.13.2")
}
