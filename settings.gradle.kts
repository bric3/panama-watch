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

rootProject.name = "panama-watch"

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

includeBuild("conventions")
include(
  "ffm-basic-examples",
  "ffm-blake3",
  "ffm-syscall-macos",
  "ffm-syscall-linux-memfdsecret",
  "ffm-touchid",
)


val os = DefaultNativePlatform.getCurrentOperatingSystem()
if (os.isMacOsX) {
  include("touchid-swift-lib")
}
