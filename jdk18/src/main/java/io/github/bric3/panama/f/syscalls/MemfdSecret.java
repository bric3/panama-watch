/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.f.syscalls;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

abstract class MemfdSecret {
  private static final MemoryAddress NULL = MemoryAddress.ofLong(0L);
  private static final int PROT_NONE = 0;
  private static final int PROT_READ = 1;
  private static final int PROT_WRITE = 2;
  private static final int PROT_EXEC = 4;
  private static final int MAP_SHARED = 1;
  private static final int MAP_PRIVATE = 2;
  private static final int MAP_FIXED = 16;

  private static final CLinker systemCLinker = CLinker.systemCLinker();
  public static final MethodHandle __errnoLocationMH = systemCLinker.downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS));
  public static final MethodHandle strerror = systemCLinker.downcallHandle(
          systemCLinker.lookup("strerror").get(),
          FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  );
  // int close(int fd);
  private static final MethodHandle close = systemCLinker.downcallHandle(
          systemCLinker.lookup("close").get(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT // fd
          )
  );
  // int munmap(void *addr, size_t length);
  private static final MethodHandle munmap = systemCLinker.downcallHandle(
          systemCLinker.lookup("munmap").get(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS, // addr
                  ValueLayout.JAVA_LONG // length
          )
  );
  // void *mmap(void *addr, size_t lengthint " prot ", int " flags, int fd, off_t offset)
  public static final MethodHandle mmap = systemCLinker.downcallHandle(
          systemCLinker.lookup("mmap").get(),
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
  public static final MethodHandle ftruncate = systemCLinker.downcallHandle(
          systemCLinker.lookup("ftruncate").get(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT, // fd
                  ValueLayout.JAVA_LONG // length
          )
  );
  // #define SYS_memfd_secret 447
  private static final MethodHandle sys_memfd_secret = MethodHandles.insertArguments(systemCLinker.downcallHandle(
          systemCLinker.lookup("syscall").get(),
          FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT // syscall number
          ).appendArgumentLayouts(ValueLayout.JAVA_INT) // flags
  ), 0, 447);
  public static final int ENOSYS = 38;

  private MemfdSecret(ResourceScope scope) {
  }

  public static Optional<MemorySegment> create(long length, ResourceScope scope) {
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

      var segmentAddress = (MemoryAddress) mmap.invoke(
              NULL,
              length,
              PROT_READ | PROT_WRITE,
              MAP_SHARED,
              fd,
              0
      );
      if (segmentAddress.toRawLongValue() == -1) {
        var errno = errno();
        System.err.println("mmap failed, errno: " + errno + ", " + strerror(errno));
        return Optional.empty();
      }

      return Optional.of(MemorySegment.ofAddress(segmentAddress, length, scope));
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


    var errnoPointer = (MemoryAddress) __errnoLocationMH.invoke(
            systemCLinker.lookup("__errno_location").get()
    );
    return errnoPointer.get(ValueLayout.JAVA_INT, 0);
  }

  // char *strerror(int errnum);
  private static String strerror(int errno) throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())
    return ((MemoryAddress) strerror.invoke(errno)).getUtf8String(0);
  }
}
