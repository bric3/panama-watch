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
  `swift-library`
  // `xctest` to support building and running test executables (linux) or bundles (macos)
}

library {
  linkage.set(listOf(Linkage.SHARED, Linkage.STATIC))
  targetMachines.set(listOf(machines.linux.x86_64, machines.macOS.x86_64))
  module.set("TouchIdDemoLib")

  // Set compiler flags here due to bug
  // https://github.com/gradle/gradle/issues/18439
  binaries.configureEach(SwiftSharedLibrary::class) {
    compileTask.get().run {
      optimized.set(false)
      debuggable.set(false)
    }
  }
  binaries.configureEach(SwiftStaticLibrary::class) {
    compileTask.get().run {
      optimized.set(false)
      debuggable.set(false)
    }
  }
}

tasks.assemble {
  tasks.findByName("assembleReleaseStaticMacos")?.let {
    dependsOn(it)
    doLast {
      println("static") // if Linkage.STATIC
      println("release " + (tasks.named("createReleaseStaticMacos").get() as CreateStaticLibrary).outputFile.get())
      println("debug " + (tasks.named("createDebugStaticMacos").get() as CreateStaticLibrary).outputFile.get())
    }
  }

  tasks.findByName("assembleReleaseSharedMacos")?.let {
    dependsOn(it)
    doLast {
      println("lib") // if Linkage.SHARED
      println("release " + (tasks.named("linkReleaseSharedMacos").get() as LinkSharedLibrary).linkedFile.get())
      println("debug " + (tasks.named("linkDebugSharedMacos").get() as LinkSharedLibrary).linkedFile.get())
    }
  }
}