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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Blake3Sum {

  public static void main(String[] args) throws IOException {
    var path = Path.of("/Users/brice.dutheil/Downloads/openjdk-21-jextract+1-2_macos-x64_bin.tar.gz");
    System.load("/Users/brice.dutheil/opensource/BLAKE3/c/libblake3.so");

    try (Arena arena = Arena.ofConfined();
         FileChannel channel = FileChannel.open(path)) {
      var start = System.nanoTime();

      var hasher = blake3_hasher.allocate(arena);
      blake3_h.blake3_hasher_init(hasher);

      var content = channel.map(
              MapMode.READ_ONLY,
              0,
              Files.size(path),
              arena
      );

      blake3_h.blake3_hasher_update(hasher, content, content.byteSize());

      var out = arena.allocate(blake3_h.BLAKE3_OUT_LEN());
      blake3_h.blake3_hasher_finalize(hasher, out, blake3_h.BLAKE3_OUT_LEN());

      var end = System.nanoTime();

      var sigBytes = out.toArray(ValueLayout.JAVA_BYTE);
      var sigHex = HexFormat.of().formatHex(sigBytes);
      System.out.println(sigHex);
      System.out.println("time: " + (end - start) / 1000000 + "ms");
      
      assert sigHex.equalsIgnoreCase("6b1b63cf578e129b38ba424f18cc9f12956d1e3d38206e225aeeb4fd53eaae49") : "oups";
    }
  }
}


