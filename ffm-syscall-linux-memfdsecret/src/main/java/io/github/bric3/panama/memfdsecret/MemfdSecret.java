/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.memfdsecret;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

abstract class MemfdSecret {
  private static final int PROT_NONE = 0;
  private static final int PROT_READ = 1;
  private static final int PROT_WRITE = 2;
  private static final int PROT_EXEC = 4;
  private static final int MAP_SHARED = 1;
  private static final int MAP_PRIVATE = 2;
  private static final int MAP_FIXED = 16;

  private static final Linker linker = Linker.nativeLinker();
  private static SymbolLookup symbolLookup = linker.defaultLookup();
  public static final MethodHandle __errnoLocationMH = linker.downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS));
  public static final MethodHandle strerror = linker.downcallHandle(
          symbolLookup.find("strerror").orElseThrow(),
          FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  );

    // int close(int fd);
  private static final MethodHandle close = linker.downcallHandle(
          symbolLookup.find("close").orElseThrow(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT // fd
          )
  );
  // int munmap(void *addr, size_t length);
  private static final MethodHandle munmap = linker.downcallHandle(
          symbolLookup.find("munmap").orElseThrow(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS, // addr
                  ValueLayout.JAVA_LONG // length
          )
  );
  // void *mmap(void *addr, size_t lengthint " prot ", int " flags, int fd, off_t offset)
  public static final MethodHandle mmap = linker.downcallHandle(
          symbolLookup.find("mmap").orElseThrow(),
          FunctionDescriptor.of(
                  ValueLayout.ADDRESS, // addr
                  ValueLayout.ADDRESS, // addr
                  ValueLayout.JAVA_LONG, // size
                  ValueLayout.JAVA_INT, // protection modes
                  ValueLayout.JAVA_INT, // flags
                  ValueLayout.JAVA_INT, // fd
                  ValueLayout.JAVA_LONG // offset
          )
  );
  // int ftruncate(int fd, off_t length);
  public static final MethodHandle ftruncate = linker.downcallHandle(
          symbolLookup.find("ftruncate").orElseThrow(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT, // fd
                  ValueLayout.JAVA_LONG // length
          )
  );
  // #define SYS_memfd_secret 447
  private static final MethodHandle sys_memfd_secret = MethodHandles.insertArguments(linker.downcallHandle(
          symbolLookup.find("syscall").orElseThrow(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT // syscall number
          ).appendArgumentLayouts(ValueLayout.JAVA_INT) // flags
  ), 0, 447);
  public static final int ENOSYS = 38;

  private MemfdSecret() {
  }

  public static Optional<MemorySegment> create(long length, Arena arena) {
    int fd = -1;
    try {
      // Create the anonymous RAM-based file
      fd = (int) sys_memfd_secret.invoke(0);
      if (fd == -1) {
        // non-existent system call, errno will be set to ENOSYS.
        // gated by secretmem_enable
        // https://github.com/torvalds/linux/commit/1507f51255c9ff07d75909a84e7c0d7f3c4b2f49#diff-659f2a8bad777301f059a00056336b415c41e024f88280a2131e0eabd7507b91R186-R187
        var errno = errno();
        System.err.println(errno == ENOSYS ?
                           "tried to call a syscall that doesn't exist (errno=ENOSYS), may need to set the 'secretmem.enable=1' kernel boot option" :
                           "syscall memfd_secret failed, errno: " + errno + ", " + strerror(errno));
        return Optional.empty();
      }
      System.out.println("Secret mem fd: " + fd);

      var res = (int) ftruncate.invoke(fd, length);
      if (res == -1) {
        var errno = errno();
        System.err.println("ftruncate failed, errno: " + errno + ", " + strerror(errno));
      }

      var segmentAddress = (MemorySegment) mmap.invoke(
              MemorySegment.NULL,
              length,
              PROT_READ | PROT_WRITE,
              MAP_SHARED,
              fd,
              0
      );
      if (segmentAddress.address() == -1) {
        var errno = errno();
        System.err.println("mmap failed, errno: " + errno + ", " + strerror(errno));
        return Optional.empty();
      }

      return Optional.of(MemorySegment.ofAddress(segmentAddress.address()).reinterpret(length, arena, null));
    } catch (Throwable e) {
      throw new AssertionError("Should not reach here", e);
    } finally {
      if (fd >= 0) {
        try {
          close.invoke(fd);
        } catch (Throwable e) {
          throw new AssertionError("Should not reach here", e);
        }
      }
    }
  }

  // /usr/include/asm-generic/errno.h
  // /usr/include/asm-generic/errno-base.h
  private static int errno() throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())


    var errnoPointer = (MemorySegment) __errnoLocationMH.invoke(
            linker.defaultLookup().find("__errno_location").orElseThrow()
    );
    return errnoPointer.get(ValueLayout.JAVA_INT, 0);
  }

  // char *strerror(int errnum);
  private static String strerror(int errno) throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())
    return ((MemorySegment) strerror.invoke(errno)).getString(0);
  }
}
