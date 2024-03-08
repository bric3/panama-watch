/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  id("panama.java-conventions")
}

/// ___          ___
///  | _     _|_  |  _|
///  |(_)|_|(_| |_|_(_|
///////////////////////////////////////////////////////////////////////

// See https://docs.gradle.org/current/userguide/cross_project_publications.html
val os = DefaultNativePlatform.getCurrentOperatingSystem()
when {
    os.isMacOsX -> {
      val swiftLib by tasks.registering {
        val helloSwiftLibAssembleTask = project(":hello-swift-lib").tasks.assemble
        dependsOn(helloSwiftLibAssembleTask)
        doLast {
          val sharedLib = helloSwiftLibAssembleTask.get().outputs.files.filter { it.isFile }
          copy {
            from(sharedLib.asFileTree)
            into(sourceSets.main.get().output.resourcesDir!!)
          }
        }
      }

      tasks.withType<JavaExec>().configureEach {
        dependsOn(swiftLib) // for IntelliJ run main class
      }

      // JAVA_LIBRARY_PATH=.../build/resources/main java --enable-native-access=ALL-UNNAMED -cp .../build/classes/java/main sandbox.TouchId
      val helloSwift by tasks.registering(JavaExec::class) {
        dependsOn(tasks.compileJava, swiftLib)
        mainClass.set("sandbox.TouchId")
        environment = mapOf(
          "JAVA_LIBRARY_PATH" to sourceSets.main.get().output.resourcesDir!!
          // "JAVA_LIBRARY_PATH" to ".:/usr/local/lib"
        )
      }
    }
    else -> logger.warn("Touch Id example disabled as biometerics are not qvilable on other platforms")
}
