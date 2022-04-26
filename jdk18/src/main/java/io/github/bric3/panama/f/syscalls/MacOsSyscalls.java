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
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.ValueLayout;
import unistd.syscall_h;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

public class MacOsSyscalls {

  public static void main(String[] args) throws Throwable {
    if (!System.getProperty("os.name").toLowerCase().contains("mac")
        || !System.getProperty("os.arch").toLowerCase().contains("x86_64")) {
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
    syscallWithArguments();
    syscallWithJextract();
  }

  private static void noArgSyscalls() throws Throwable {
    System.out.println("Syscalls");
    var systemCLinker = CLinker.systemCLinker();

    var syscall = systemCLinker.downcallHandle(
            systemCLinker.lookup("syscall").get(),
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
    var systemCLinker = CLinker.systemCLinker();

    var syscall = systemCLinker.downcallHandle(
            systemCLinker.lookup("syscall").get(),
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
    var systemCLinker = CLinker.systemCLinker();

    var syscall = systemCLinker.downcallHandle(
            systemCLinker.lookup("syscall").get(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            ).appendArgumentLayouts(ValueLayout.ADDRESS)
    );

    // #define	SYS_mkdir          136
    var sys_mkdir = MethodHandles.insertArguments(syscall, 0, 136);

    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
      var str = SegmentAllocator.nativeAllocator(scope).allocateUtf8String(path.toString());

      int res = (int) sys_mkdir.invoke(str);
    }

    Files.getPosixFilePermissions(path).forEach(System.out::println); // dir created without permissions
  }

  private static void syscallWithJextract() throws Throwable {
    System.out.println("Syscalls with jextract");
    var path = Path.of("/tmp/create-new-directory-here2");
    Files.deleteIfExists(path);


    try (ResourceScope scope = ResourceScope.newConfinedScope()) {
      var str = SegmentAllocator.nativeAllocator(scope).allocateUtf8String(path.toString());

      // #define	SYS_mkdir          136
      int mkdirRes = syscall_h.syscall(syscall_h.SYS_mkdir(), str.address());

      // #define	SYS_chmod          15
      @SuppressWarnings("OctalInteger")
      int res = unistd.syscall_h.syscall(syscall_h.SYS_chmod(), str.address(), 0744);
    }

    Files.getPosixFilePermissions(path).forEach(System.out::println); // dir created without permissions
  }
}