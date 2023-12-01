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
  // doc: https://docs.gradle.org/current/userguide/kotlin_dsl.html
  `kotlin-dsl`
}

repositories {
  mavenCentral()
}

dependencies {
  // https://github.com/gradle/gradle/issues/15383
  // implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}