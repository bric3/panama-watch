/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.c.blake3;

import blake3.blake3_h;
import blake3.blake3_hasher;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.ValueLayout;

import java.util.HexFormat;

public class Blake3 {

  public static void main(String[] args) {
    System.load("/Users/brice.dutheil/opensource/BLAKE3/c/libblake3.so");

    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
      var segmentAllocator = SegmentAllocator.newNativeArena(scope);

      var hasher = blake3_hasher.allocate(scope);
      blake3_h.blake3_hasher_init(hasher);


      var content = segmentAllocator.allocateUtf8String("Hello panama!\n");


      blake3_h.blake3_hasher_update(hasher, content, content.byteSize() - 1);


      var out = MemorySegment.allocateNative(
              MemoryLayout.sequenceLayout(blake3_h.BLAKE3_OUT_LEN(), ValueLayout.JAVA_BYTE),
              scope
      );
      blake3_h.blake3_hasher_finalize(hasher, out, blake3_h.BLAKE3_OUT_LEN());


      var sigBytes = out.toArray(ValueLayout.JAVA_BYTE);
      var sigHex = HexFormat.of().formatHex(sigBytes);
      System.out.println(sigHex);

      assert sigHex.equalsIgnoreCase("b95c35ea189068be5f737282c3248277a6398fab7826ef128607e4415ab8558d") : "oups";
    }
  }
}


