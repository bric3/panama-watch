/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.d.mmap;

import blake3.blake3_h;
import blake3.blake3_hasher;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.ValueLayout;

import java.io.IOException;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Blake3File {

  public static void main(String[] args) throws IOException {
    var path = Path.of("/Users/brice.dutheil/Downloads/clang+llvm-14.0.0-x86_64-apple-darwin.tar.xz");
    System.load("/Users/brice.dutheil/opensource/BLAKE3/c/libblake3.so");

    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
      var segmentAllocator = SegmentAllocator.newNativeArena(scope);
      var start = System.nanoTime();

      var hasher = blake3_hasher.allocate(scope);
      blake3_h.blake3_hasher_init(hasher);


      var content = MemorySegment.mapFile(
              path,
              0,
              Files.size(path),
              MapMode.READ_ONLY,
              scope
      );
      blake3_h.blake3_hasher_update(hasher, content, content.byteSize());


      var out = MemorySegment.allocateNative(blake3_h.BLAKE3_OUT_LEN(), scope);
      blake3_h.blake3_hasher_finalize(hasher, out, blake3_h.BLAKE3_OUT_LEN());

      var end = System.nanoTime();

      var sigBytes = out.toArray(ValueLayout.JAVA_BYTE);
      var sigHex = HexFormat.of().formatHex(sigBytes);
      System.out.println(sigHex);
      System.out.println("time: " + (end - start) / 1000000 + "ms");
      
      assert sigHex.equalsIgnoreCase("4c600f39160d930ae8b5d55e924b8630900b9bad39998d25ca761b4eed9528fe") : "oups";
    }
  }
}


