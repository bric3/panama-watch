/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

plugins {
  java
}

val javaVersion = 22
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(javaVersion))
  }
}

tasks {
  withType<JavaExec>().configureEach {
    dependsOn(tasks.compileJava) // for IntelliJ run main class
    group = "class-with-main"
    classpath(sourceSets.main.get().runtimeClasspath)

    // Need to set the toolchain https://github.com/gradle/gradle/issues/16791
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs(
      "-ea",
      "--enable-native-access=ALL-UNNAMED",
      "--enable-preview"
    )

    environment = mapOf(
      "JAVA_LIBRARY_PATH" to sourceSets.main.get().output.resourcesDir!! // for IntelliJ run main class
      // "JAVA_LIBRARY_PATH" to ".:/usr/local/lib"
    )
  }


  withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.compilerArgs = listOf(
      "--enable-preview",
      "-Xlint:preview"
    )
    options.isFailOnError = false
  }
}