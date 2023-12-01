/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.e.upcall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Qsort {
  public static void main(String[] args) throws Throwable {
    var ints = ThreadLocalRandom.current().ints(1000, 0, Integer.MAX_VALUE).toArray();

    var LINKER = Linker.nativeLinker();

    var qsort = LINKER.downcallHandle(
            LINKER.defaultLookup().find("qsort").orElseThrow(),
            FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // pointer to the array
                    ValueLayout.JAVA_LONG, // size of the array
                    ValueLayout.JAVA_LONG, // size of the element
                    ValueLayout.ADDRESS // pointer to the comparison function
            )
    );

    var compareDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT),
            ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT)
    );
    var compareHandle = MethodHandles.lookup()
                                     .findStatic(
                                             Qsort.class,
                                             "qsortCompare",
                                             MethodType.methodType(
                                                     int.class,
                                                     MemorySegment.class,
                                                     MemorySegment.class
                                             )
                                     );

    try (Arena arena = Arena.ofConfined()) {
      var compareUpcallSymbol = LINKER.upcallStub(compareHandle, compareDescriptor, arena);

      var nativeArraySegment = arena.allocateArray(ValueLayout.JAVA_INT, ints);

      qsort.invoke(
              nativeArraySegment,
              ints.length,
              ValueLayout.JAVA_INT.byteSize(),
              compareUpcallSymbol
      );

      System.out.println(Arrays.toString(nativeArraySegment.toArray(ValueLayout.JAVA_INT)));
    }

  }

  static int qsortCompare(MemorySegment addr1, MemorySegment addr2) {
    return Integer.compare(
            addr1.get(ValueLayout.JAVA_INT, 0),
            addr2.get(ValueLayout.JAVA_INT, 0)
    );
  }
}
