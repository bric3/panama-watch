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

import java.io.FileDescriptor;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

/**
 * Invoke <code>memfd_secret</code> syscall via panama.
 * <p>
 * Linux 5.14 introduces the new syscall <code>memfd_secret</code> which allows creating a
 * anonymous file descriptor that cannot be accessed from the kernel once it's
 * mapped by the user program.
 * <p>
 * Info :
 * <a href="https://github.com/torvalds/linux/commit/1507f51255c9ff07d75909a84e7c0d7f3c4b2f49">commit <code>1507f51255c9ff07d75909a84e7c0d7f3c4b2f49</code></a>
 * <p>
 * Note in order to work, the machine boot has to be configured with
 * <code>secretmem.enable=1</code>
 * <p>
 * Eg (according to <a href="https://docs.fedoraproject.org/en-US/fedora/latest/system-administrators-guide/kernel-module-driver-configuration/Working_with_the_GRUB_2_Boot_Loader/">Fedora: working with the bootloader</a>
 * and if the file <code>/etc/default/grub</code> has an entry <code>GRUB_ENABLE_BLSCFG=true</code>)
 * <pre><code>sudo grubby --update-kernel=ALL --args="secretmem.enable=1"</code></pre>
 * Check config
 * <pre><code>sudo grubby --info=ALL</code></pre>
 * Remove
 * <pre><code>sudo grubby --update-kernel=ALL --remove-args="secretmem.enable=1"</code></pre>.
 *
 * <strong>Note</strong> : Enabling this prevents hibernation whenever there are active secret memory users.
 * <p>
 * Specific command line args :
 * <pre><code>
 * java --enable-native-access=ALL-UNNAMED LinuxSyscall.java
 * </code></pre>
 */
public class LinuxSyscall {
  private static final MemorySegment NULL = MemorySegment.NULL;
  private static final int PROT_NONE = 0;
  private static final int PROT_READ = 1;
  private static final int PROT_WRITE = 2;
  private static final int PROT_EXEC = 4;
  private static final int MAP_SHARED = 1;
  private static final int MAP_PRIVATE = 2;
  private static final int MAP_FIXED = 16;

  private static final Linker LINKER = Linker.nativeLinker();

  public static void main(String[] args) throws Throwable {
    if (!System.getProperty("os.name").toLowerCase().contains("linux")
        || !System.getProperty("os.arch").toLowerCase().contains("amd64")) {
      System.err.println("This program only runs on Linux amd64");
      System.exit(1);
    }
    if (ProcessHandle.current().info().commandLine()
                     .filter(cl -> cl.contains("--enable-native-access=ALL-UNNAMED"))
                     .isEmpty()) {
      System.err.println("This program requires --enable-native-access=ALL-UNNAMED");
      System.exit(1);
    }
    System.out.println(STR."OS version: \{System.getProperty("os.version")}");
    // Reference:
    // /usr/include/asm/unistd_64.h

    // https://github.com/mkerrisk/man-pages/blob/ae6b221882ce71ba82fcdbe02419a225111502f0/man2/memfd_secret.2
    memfd_secret_external();
    memfd_secret();
    // --add-opens=java.base/java.io=ALL-UNNAMED
    memfd_secret_avoid_syscall();
  }

  private static void memfd_secret() throws Throwable {
    System.out.println("starting memfd_secret");
    var secret = "p@ss123";

    // #define SYS_memfd_secret 447
    var sys_memfd_secret = MethodHandles.insertArguments(LINKER.downcallHandle(
            LINKER.defaultLookup().find("syscall").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT // syscall number
            ).appendArgumentLayouts(ValueLayout.JAVA_INT) // flags
    ), 0, 447);
    // int ftruncate(int fd, off_t length);
    var ftruncate = LINKER.downcallHandle(
            LINKER.defaultLookup().find("ftruncate").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, // fd
                    ValueLayout.JAVA_LONG // length
            )
    );
    // void *mmap(void *addr, size_t lengthint " prot ", int " flags, int fd, off_t offset)
    var mmap = LINKER.downcallHandle(
            LINKER.defaultLookup().find("mmap").orElseThrow(),
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
    // int munmap(void *addr, size_t length);
    var munmap = LINKER.downcallHandle(
            LINKER.defaultLookup().find("munmap").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // addr
                    ValueLayout.JAVA_LONG // length
            )
    );
    // int close(int fd);
    var close = LINKER.downcallHandle(
            LINKER.defaultLookup().find("close").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT // fd
            )
    );

    // Create the anonymous RAM-based file
    int fd = (int) sys_memfd_secret.invoke(0);
    if (fd == -1) {
      // non-existent system call, errno will be set to ENOSYS.
      // gated by secretmem_enable
      // https://github.com/torvalds/linux/commit/1507f51255c9ff07d75909a84e7c0d7f3c4b2f49#diff-659f2a8bad777301f059a00056336b415c41e024f88280a2131e0eabd7507b91R186-R187
      var errno = errno();
      System.err.println(errno == 38 ?
                         "tried to call a syscall that doesn't exist (errno=ENOSYS), may need to set the 'secretmem.enable=1' kernel boot option" :
                         STR."syscall memfd_secret failed, errno: \{errno()}");
      System.exit(1);
    }
    System.out.println(STR."Secret mem fd: \{fd}");

    try (Arena scope = Arena.ofConfined()) {
      // Set the size
      System.out.println("Setting size");
      var res = (int) ftruncate.invoke(fd, secret.length());
      if (res == -1) {
        System.err.println(STR."ftruncate failed, errno: \{errno()}");
      }

      System.out.println("Mapping");
      var segmentAddress = (MemorySegment) mmap.invoke(NULL, secret.length(), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
      if (segmentAddress.address() == -1) {
        System.err.println(STR."mmap failed, errno: \{errno()}, \{strerror(errno())}");
        System.exit(1);
      }

      System.out.println(STR."segmentAddress: \{segmentAddress}");

      segmentAddress.setString(0, secret);


      System.out.println(STR."Secret segment contained: \{segmentAddress.getString(0)}");;

      var r = (int) munmap.invoke(segmentAddress, secret.length());
    } finally {
      if (fd >= 0) {
        close.invoke(fd);
      }
    }
  }

  private static void memfd_secret_avoid_syscall() throws Throwable {
    System.out.println("starting memfd_secret avoiding syscalls");
    var secret = "p@ss123";


    // #define SYS_memfd_secret 447
    var sys_memfd_secret = MethodHandles.insertArguments(LINKER.downcallHandle(
            LINKER.defaultLookup().find("syscall").orElseThrow(),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT // syscall number
            ).appendArgumentLayouts(ValueLayout.JAVA_INT) // flags
    ), 0, 447);

    // Create the anonymous RAM-based file
    int fd = (int) sys_memfd_secret.invoke(0);
    if (fd == -1) {
      // non-existent system call, errno will be set to ENOSYS.
      // gated by secretmem_enable
      // https://github.com/torvalds/linux/commit/1507f51255c9ff07d75909a84e7c0d7f3c4b2f49#diff-659f2a8bad777301f059a00056336b415c41e024f88280a2131e0eabd7507b91R186-R187
      var errno = errno();
      System.err.println(errno == 38 ?
                         "tried to call a syscall that doesn't exist (errno=ENOSYS), may need to set the 'secretmem.enable=1' kernel boot option" :
                         "syscall memfd_secret failed, errno: " + errno);
      System.exit(1);
    }
    System.out.println("Secret mem fd: " + fd);

    try (Arena scope = Arena.ofConfined()) {

      // NoSuchFile when trying to read again from the same fd

      // var memfd = makeFD(fd);
      // try (var fos = new FileOutputStream(memfd);
      //      var fis = new FileInputStream(memfd);) {
      //   var writeOnly = fos.getChannel().map( // => java.nio.channels.NonReadableChannelException
      //           MapMode.READ_WRITE,
      //           0,
      //           secret.length() + 1
      //   );
      //   var readOnly = fis.getChannel().map(
      //           MapMode.READ_ONLY,
      //           0,
      //           secret.length() + 1
      //   );
      //
      //   writeOnly.put(secret.getBytes());
      //
      //   readOnly.flip();
      //   var bytes = new byte[secret.length()];
      //   readOnly.get(bytes);
      //   System.out.println("Secret read: " + new String(bytes));
      // }

      // var secretSegment = MemorySegment.mapFile(
      //         // Files.readSymbolicLink(Path.of("/proc/self/fd/" + fd)), // => java.nio.file.NoSuchFileException: /secretmem (deleted)
      //         Path.of("/proc/self/fd/" + fd), // => java.nio.file.FileSystemException: /proc/self/fd/4: No such device or address
      //         0,
      //         secret.length() + 1,
      //         MapMode.READ_WRITE,
      //         scope
      // );
      //
      // secretSegment.setUtf8String(0, secret);
      //
      // System.out.println("Secret segment: " + secretSegment.getUtf8String(0));;
      
      // var mappedByteBuffer = FileChannel.open(Files.readSymbolicLink(Path.of("/proc/self/fd/" + fd)),
      //                            StandardOpenOption.READ,
      //                            StandardOpenOption.WRITE
      // ).map(MapMode.READ_WRITE, 0, secret.length());
      // mappedByteBuffer.put(secret.getBytes());
      //
      // TimeUnit.SECONDS.sleep(15);
      //
      // mappedByteBuffer.flip();
      //
      //
      // var bytes = new byte[secret.length()];
      // mappedByteBuffer.get(bytes);
      //
      // System.out.println("Secret read: " + new String(bytes));
    }
  }

  private static void memfd_secret_external() {
    var secret = "secret decryption key".getBytes(StandardCharsets.UTF_8);

    try (var arena = Arena.ofConfined()) {
      var secretSegment = MemfdSecret.create(secret.length, arena).orElseThrow();
      secretSegment.isNative(); // => true


      var onHeap = MemorySegment.ofArray(secret);
      onHeap.isNative(); // => false
      var readOnlySecretSegment = secretSegment.copyFrom(onHeap).asReadOnly();


      System.out.println("Secret: " + new String(readOnlySecretSegment.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8));
    }
  }

  private static FileDescriptor makeFD(int fd) throws Throwable {
    // requires --add-opens=java.base/java.io=ALL-UNNAMED
    var fdInit = MethodHandles.privateLookupIn(FileDescriptor.class, MethodHandles.lookup())
                              .findConstructor(FileDescriptor.class, MethodType.methodType(void.class, int.class));

    return (FileDescriptor) fdInit.invoke(fd);
  }

  // /usr/include/asm-generic/errno.h
  // /usr/include/asm-generic/errno-base.h
  private static int errno() throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())


    var __errnoLocationMH = LINKER.downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS));
    var errnoPointer = (MemorySegment) __errnoLocationMH.invoke(LINKER.defaultLookup().find("__errno_location").get());
    return errnoPointer.get(ValueLayout.JAVA_INT, 0);
  }

  // char *strerror(int errnum);
  private static String strerror(int errno) throws Throwable {
    // /* The error code set by various library functions.  */
    // extern int *__errno_location (void) __THROW __attribute_const__;
    // # define errno (*__errno_location ())


    var strerror = LINKER.downcallHandle(
            LINKER.defaultLookup().find("strerror").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
    );

    return ((MemorySegment) strerror.invoke(errno)).getString(0);
  }
}



/*
got address
segmentAddress: MemoryAddress{ offset=0xffffffffffffffff }
=> actually `-1`
so when writing at -1 it triggers a segmentation fault


#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00007f561919ffd7, pid=4798, tid=4799
#
# JRE version: OpenJDK Runtime Environment 22.3 (18.0+37) (build 18+37)
# Java VM: OpenJDK 64-Bit Server VM 22.3 (18+37, mixed mode, sharing, tiered, compressed oops, compressed class ptrs, g1 gc, linux-amd64)
# Problematic frame:
# v  ~StubRoutines::jbyte_disjoint_arraycopy
#
# Core dump will be written. Default location: Core dumps may be processed with "/usr/lib/systemd/systemd-coredump %P %u %g %s %t %c %h" (or dumping to /home/bob/opensource/core.4798)
#
# An error report file with more information is saved as:
# /home/bob/opensource/hs_err_pid4798.log
#
# If you would like to submit a bug report, please visit:
#   https://bugzilla.redhat.com/enter_bug.cgi?product=Fedora&component=java-latest-openjdk&version=35
#
 */
