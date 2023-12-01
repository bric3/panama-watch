/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.syscall;

import unistd.syscall_h;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class MacOsSyscalls {

  public static void main(String[] args) throws Throwable {
    if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
      System.err.println("This program only runs on Macos x86_64");
      System.exit(1);
    }
    // Reference:
    // /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/syscall.h

    // https://brennan.io/2016/11/14/kernel-dev-ep3/
    // https://man7.org/linux/man-pages/man2/syscall.2.html

    // System calls can be used in C via defines that depends on the
    // architecture. For x84_64, the syscall numbers are defined in:
    // /usr/include/asm/unistd64.h

    // Looking at /usr/include/sys/syscall.h
    //    The Linux kernel header file defines macros __NR_*, but some
    //    programs expect the traditional form SYS_*.  <bits/syscall.h>
    //    defines SYS_* macros for __NR_* macros of known names.

    // https://unix.stackexchange.com/questions/421750/where-do-you-find-the-syscall-table-for-linux
    // printf SYS_memfd_secret | gcc -include sys/syscall.h -E -

    // grep '^asmlinkage.*sys_' /usr/src/kernels/5.15.11-200.fc35.x86_64/include/linux/syscalls.h
    // asmlinkage long sys_memfd_secret(unsigned int flags);

    // syscall_h.syscall(syscall_h.SYS_getpid());

    noArgSyscalls();
    noArgSyscallsWithBindings();

    // Failing on M1 with EILSEQ : Illegal byte sequence
    syscallWithArguments();
    syscallWithJextract();
  }
  private static Linker systemLinker = Linker.nativeLinker();
  public static final MethodHandle __error = systemLinker.downcallHandle(FunctionDescriptor.of(
          ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(JAVA_BYTE))
  ));
  public static final MethodHandle strerror = systemLinker.downcallHandle(
          systemLinker.defaultLookup().find("strerror").orElseThrow(),
          FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
  );


  private static void noArgSyscalls() throws Throwable {
    System.out.println("Syscalls");

    var syscall = systemLinker.downcallHandle(
            systemLinker.defaultLookup().find("syscall").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            )
    );

    // #define	SYS_getpid         20
    int pid = (int) syscall.invoke(20);
    // #define	SYS_getuid         24
    int uid = (int) syscall.invoke(24);
    // #define	SYS_getgid         47
    int gid = (int) syscall.invoke(47);
    System.out.println("pid: " + pid);
    System.out.println("uid: " + uid); // $ id -u
    System.out.println("gid: " + gid); // $ id -g
  }

  private static void noArgSyscallsWithBindings() throws Throwable {
    System.out.println("Syscalls with MH bindings");
    var systemLinker = Linker.nativeLinker();

    var syscall = systemLinker.downcallHandle(
            systemLinker.defaultLookup().find("syscall").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            )
    );

    // #define	SYS_getpid         20
    var sys_getpid = MethodHandles.insertArguments(syscall, 0, 20);
    // #define	SYS_getuid         24
    var sys_getuid = MethodHandles.insertArguments(syscall, 0, 24);
    // #define	SYS_getgid         47
    var sys_getgid = MethodHandles.insertArguments(syscall, 0, 47);

    int pid = (int) sys_getpid.invoke();
    int uid = (int) sys_getuid.invoke();
    int gid = (int) sys_getgid.invoke();
    System.out.println("pid: " + pid);
    System.out.println("uid: " + uid); // $ id -u
    System.out.println("gid: " + gid); // $ id -g

  }

  private static void syscallWithArguments() throws Throwable {
    System.out.println("Syscalls with args");
    var path = Path.of("/tmp/create-new-directory-here");
    Files.deleteIfExists(path);
    var systemLinker = Linker.nativeLinker();

    var syscall = systemLinker.downcallHandle(
            systemLinker.defaultLookup().find("syscall").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            ).appendArgumentLayouts(ValueLayout.ADDRESS)
    );

    // #define	SYS_mkdir          136
    var sys_mkdir = MethodHandles.insertArguments(syscall, 0, 136);

    try (Arena arena = Arena.ofConfined()) {
      var str = arena.allocateUtf8String(path.toString());

      int res = (int) sys_mkdir.invokeExact(str);
      if (res == -1) {
        System.err.println(strerror(errno()));
      }
    }

    Files.getPosixFilePermissions(path).forEach(System.out::println); // dir created without permissions
  }

  private static void syscallWithJextract() throws Throwable {
    System.out.println("Syscalls with jextract");
    var path = Path.of("/tmp/create-new-directory-here2");
    Files.deleteIfExists(path);


    try (Arena arena = Arena.ofConfined()) {
      var str = arena.allocateUtf8String(path.toString());

      // #define	SYS_mkdir          136
      int mkdirRes = syscall_h.syscall(syscall_h.SYS_mkdir(), str.address());
      if (mkdirRes == -1) {
        System.err.println("errno: " + strerror(errno()));
      }


      // #define	SYS_chmod          15
      @SuppressWarnings("OctalInteger")
      int res = unistd.syscall_h.syscall(syscall_h.SYS_chmod(), str.address(), 0744);
    }

    Files.getPosixFilePermissions(path).forEach(System.out::println); // dir created without permissions
  }


  // /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/errno.h
  // /Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/sys/errno.h

  private static int errno() throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int * __error(void);
    // #define errno (*__error())


    var errnoPointer = (MemorySegment) __error.invokeExact(
            systemLinker.defaultLookup().find("__error").orElseThrow()
    );
    return errnoPointer.get(JAVA_BYTE, 0);
  }

  // char *strerror(int errnum);
  private static String strerror(int errno) throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())
    return ((MemorySegment) strerror.invokeExact(errno)).reinterpret(Long.MAX_VALUE).getUtf8String(0);
  }
}