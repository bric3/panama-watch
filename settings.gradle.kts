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
include("jdk18")

val os = DefaultNativePlatform.getCurrentOperatingSystem()
if (os.isMacOsX) {
    include("touchid-swift-lib")
}
