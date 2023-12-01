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
  id("panama.java-conventions")
}

///  _          _ _
/// |_)|  /\ |/|_ _)
/// |_)|_/--\|\|_ _)
///////////////////////////////////////////////////////////////////////

val blake3SourcePath = project.configureJExtractSourceSet("blake3")

val jextractBlake3 by tasks.registering(JExtractTask::class) {
  headerClassName.set("blake3_h")
  targetPackage.set("blake3")
  targetPath.set(file(blake3SourcePath))
  headerPathIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
  headers.from(file("/Users/brice.dutheil/opensource/BLAKE3/c/blake3.h"))

  args.set(
    listOf(
      "--include-typedef", "blake3_chunk_state",
      "--include-typedef", "blake3_hasher",
      "--include-constant", "BLAKE3_BLOCK_LEN",
      "--include-constant", "BLAKE3_CHUNK_LEN",
      "--include-constant", "BLAKE3_KEY_LEN",
      "--include-constant", "BLAKE3_MAX_DEPTH",
      "--include-constant", "BLAKE3_OUT_LEN",
      "--include-constant", "BLAKE3_VERSION_STRING",
      "--include-function", "blake3_hasher_finalize",
      "--include-function", "blake3_hasher_finalize_seek",
      "--include-function", "blake3_hasher_init",
      "--include-function", "blake3_hasher_init_derive_key",
      "--include-function", "blake3_hasher_init_derive_key_raw",
      "--include-function", "blake3_hasher_init_keyed",
      "--include-function", "blake3_hasher_reset",
      "--include-function", "blake3_hasher_update",
      "--include-function", "blake3_version",
    )
  )
}
tasks.compileJava.get().dependsOn(jextractBlake3)

