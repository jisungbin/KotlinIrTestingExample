val kotlinVersion = "1.9.22"

plugins {
  kotlin("jvm") version "1.9.22"
}

sourceSets {
  val test by getting {
    java.srcDir("src/test/kotlin")
  }
}

repositories {
  mavenCentral()
}

dependencies {
  testCompileOnly(kotlin("compiler", kotlinVersion))
  testImplementation(kotlin("stdlib", kotlinVersion))
  testImplementation("junit:junit:4.13.2")
}