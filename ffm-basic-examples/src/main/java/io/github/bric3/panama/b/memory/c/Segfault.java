package io.github.bric3.panama.b.memory.c;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * This example demonstrates that a misuse of the API can still lead to a segfault
 *
 * See <a href="https://bugs.openjdk.org/browse/JDK-8322039">JDK-8322039</a>
 */
public class Segfault {
  public static void main(String[] args) {
    var basic_struct_LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(
                    64,
                    JAVA_BYTE
            ).withName("buf"),
            JAVA_BYTE.withName("buf_len"),
            JAVA_BYTE.withName("flags")
    ).withName("basic_struct");

    var pathToFlags = PathElement.groupElement("flags");
    var flags = basic_struct_LAYOUT.varHandle(
            pathToFlags
    );

    try (var arena = Arena.ofConfined()) {
      var memorySegment = arena.allocate(basic_struct_LAYOUT);
      flags.set(memorySegment, 0L, (byte) 0b0001_0001);

      // the bad code
      var retargeted = memorySegment.get(ADDRESS.withTargetLayout(basic_struct_LAYOUT), 0);
      // this crashes the VM
      byte flagsValue_JVMCrash = retargeted.get(JAVA_BYTE, basic_struct_LAYOUT.byteOffset(pathToFlags)); // this crashes
    }
  }
}
