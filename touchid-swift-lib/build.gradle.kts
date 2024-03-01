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
  `swift-library`
  // `xctest` to support building and running test executables (linux) or bundles (macos)
}

library {
  linkage.set(listOf(Linkage.SHARED, Linkage.STATIC))
  targetMachines.set(buildList {
    add(machines.linux.x86_64)
    val arch = DefaultNativePlatform.getCurrentArchitecture()

    when {
      arch.isArm64 -> add(machines.macOS.architecture("arm64"))
      arch.isAmd64 -> add(machines.macOS.x86_64)
      else -> throw GradleException("Unsupported architecture $arch")
    }
  })
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
  // The documentation suggests there are tasks like 'linkDebug', but in fact
  // the task name is only a prefix, and the suffixes are variants like target OS and architecture.
  // https://docs.gradle.org/current/userguide/building_swift_projects.html#sec:introducing_build_variants-swift

  // Also, those kind tasks are available but do not have output file
  // - assembleDebugSharedMacosArm64
  // - assembleDebugStaticMacosArm64
  // - assembleReleaseSharedMacosArm64
  // - assembleReleaseStaticMacosArm64

  // CreateStaticLibrary strictly necessary, but it's a good way to check which tasks are available
  tasks.filter { it is CreateStaticLibrary || it is LinkSharedLibrary }.forEach {
    // should have tasks that stats with
    // - linkDebug, linkRelease, createDebug, createRelease
    logger.info(":assemble Found: " + it.name)

    if (it is LinkSharedLibrary && it.name.startsWith("linkRelease")) {
      dependsOn(it)
      logger.info(":assemble Assigning output : " + it.linkedFile.get())
      this@assemble.outputs.file(it.linkedFile.get()).withPropertyName("path")
    }
  }
}
