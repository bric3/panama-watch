package io.github.bric3.panama.b.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class AccessingData {
  public static void main(String[] args) {
    var basic_struct_LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(
                    64,
                    JAVA_BYTE
            ).withName("buf"),
            JAVA_BYTE.withName("buf_len"),
            JAVA_BYTE.withName("flags")
    ).withName("basic_struct");

    PathElement pathToFlags = PathElement.groupElement("flags");
    var flags = basic_struct_LAYOUT.varHandle(
            pathToFlags
    );

    try (var arena = Arena.ofConfined()) {
      var memorySegment = arena.allocate(basic_struct_LAYOUT);
      flags.set(memorySegment, 0L, (byte) 0b0001_0001);

      System.out.println(STR."✅ Access data form VarHandle: \{
              byteToBinary((byte) flags.get(memorySegment, 0L))
              }.");

      var address = MemorySegment.ofAddress(memorySegment.address());
      try {
        flags.get(address, 0L);
      } catch (IndexOutOfBoundsException e) {
        System.out.println(STR."❌ Cannot access memory segment as a pointer: \{e.getMessage()}");
      }

      var reinterpreted = address.reinterpret(basic_struct_LAYOUT.byteSize());
      System.out.println(STR."✅ Access data form reinterpreted segment: \{
              byteToBinary((byte) flags.get(reinterpreted, 0L))
              }.");


      System.out.println(STR."✅ Access data form segment: \{
              byteToBinary(memorySegment.get(JAVA_BYTE, basic_struct_LAYOUT.byteOffset(pathToFlags)))
              }.");
    }
  }

  private static String byteToBinary(byte x) {
    return Integer.toBinaryString((x & 0xFF) + 0x100).substring(1);
  }
}
