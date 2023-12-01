/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.blake3;

import blake3.blake3_h;
import blake3.blake3_hasher;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.HexFormat;

public class Blake3 {

  public static void main(String[] args) {
    // gcc -shared -O3 -o libblake3.so blake3.c blake3_dispatch.c blake3_portable.c blake3_neon.c
    System.load("/Users/brice.dutheil/opensource/BLAKE3/c/libblake3.so");

    try (Arena arena = Arena.ofConfined()) {
      var hasher = blake3_hasher.allocate(arena);
      blake3_h.blake3_hasher_init(hasher);


      var content = arena.allocateUtf8String("Hello panama!\n");


      blake3_h.blake3_hasher_update(hasher, content, content.byteSize() - 1);


      var out = arena.allocate(
              MemoryLayout.sequenceLayout(blake3_h.BLAKE3_OUT_LEN(), ValueLayout.JAVA_BYTE)
      );
      blake3_h.blake3_hasher_finalize(hasher, out, blake3_h.BLAKE3_OUT_LEN());


      var sigBytes = out.toArray(ValueLayout.JAVA_BYTE);
      var sigHex = HexFormat.of().formatHex(sigBytes);
      System.out.println(sigHex);

      assert sigHex.equalsIgnoreCase("b95c35ea189068be5f737282c3248277a6398fab7826ef128607e4415ab8558d") : "oups";
    }
  }
}


