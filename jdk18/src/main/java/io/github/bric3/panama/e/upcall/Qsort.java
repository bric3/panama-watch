package io.github.bric3.panama.e.upcall;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.ValueLayout;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Qsort {
  public static void main(String[] args) throws Throwable {
    var ints = ThreadLocalRandom.current().ints(1000, 0, Integer.MAX_VALUE).toArray();

    var cLinker = CLinker.systemCLinker();

    var qsort = cLinker.downcallHandle(
            cLinker.lookup("qsort").get(),
            FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS, // pointer to the array
                    ValueLayout.JAVA_LONG, // size of the array
                    ValueLayout.JAVA_LONG, // size of the element
                    ValueLayout.ADDRESS // pointer to the comparison function
            )
    );

    var compareDescriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    var compareHandle = MethodHandles.lookup()
                                     .findStatic(Qsort.class, "qsortCompare",
                                                 CLinker.upcallType(compareDescriptor));

    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
      var allocator = SegmentAllocator.nativeAllocator(scope);

      var compareUpcallSymbol = cLinker.upcallStub(compareHandle, compareDescriptor, scope);

      var nativeArraySegment = allocator.allocateArray(ValueLayout.JAVA_INT, ints);

      qsort.invoke(
              nativeArraySegment,
              ints.length,
              ValueLayout.JAVA_INT.byteSize(),
              compareUpcallSymbol
      );

      System.out.println(Arrays.toString(nativeArraySegment.toArray(ValueLayout.JAVA_INT)));
    }

  }

  static int qsortCompare(MemoryAddress addr1, MemoryAddress addr2) {
    return addr1.get(ValueLayout.JAVA_INT, 0) - addr2.get(ValueLayout.JAVA_INT, 0);
  }
}
