/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.a.errno;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

public class HandlingErrno {
  public static final Linker LINKER = Linker.nativeLinker();
  public static final SymbolLookup SYMBOL_LOOKUP = LINKER.defaultLookup();

  /**
   * `winsize` structure.
   * <p>
   * ```
   * /*
   * * Window/terminal size structure.  This information is stored by the kernel
   * * in order to provide a consistent interface, but is not used by the kernel.
   * * /
   * struct winsize {
   * unsigned short  ws_row;         /* rows, in characters * /
   * unsigned short  ws_col;         /* columns, in characters * /
   * unsigned short  ws_xpixel;      /* horizontal size, pixels * /
   * unsigned short  ws_ypixel;      /* vertical size, pixels * /
   * };
   * ```
   */
  public static final StructLayout WINSIZE_LAYOUT =
          MemoryLayout.structLayout(
                              JAVA_SHORT.withName("ws_row"),
                              JAVA_SHORT.withName("ws_col"),
                              JAVA_SHORT.withName("ws_xpixel"),
                              JAVA_SHORT.withName("ws_ypixel")
                      )
                      .withName("winsize");

  record Winsize(short ws_row, short ws_col, short ws_xpixel, short ws_ypixel) {
    public Winsize(MemorySegment segment) {
      this(
              segment.get(JAVA_SHORT, WINSIZE_LAYOUT.byteOffset(PathElement.groupElement("ws_row"))),
              segment.get(JAVA_SHORT, WINSIZE_LAYOUT.byteOffset(PathElement.groupElement("ws_col"))),
              segment.get(JAVA_SHORT, WINSIZE_LAYOUT.byteOffset(PathElement.groupElement("ws_xpixel"))),
              segment.get(JAVA_SHORT, WINSIZE_LAYOUT.byteOffset(PathElement.groupElement("ws_ypixel")))
      );
    }
  }

  /**
   * Get window size request command.
   * ```
   * #define TIOCGWINSZ      _IOR('t', 104, struct winsize)  /* get window size *
   * ```
   */
  private static final long TIOCGWINSZ = 0x40087468;

  private static final int STDOUT_FILENO = 1;

  public static void main(String[] args) throws Throwable {
    System.out.println(ProcessHandle.current().info().commandLine().orElseThrow());
    var winsize = c_ioctl();
    System.out.println(STR."terminal: \{winsize}");
  }

  private static Winsize c_ioctl() throws Throwable {
    // int ioctl(int fildes, unsigned long request, ...);
    var ioctl = LINKER.downcallHandle(
            SYMBOL_LOOKUP.find("ioctl").orElseThrow(),
            FunctionDescriptor.of(
                    JAVA_INT,
                    JAVA_INT.withName("fd"),
                    JAVA_LONG.withName("request"),
                    ADDRESS.withTargetLayout(WINSIZE_LAYOUT).withName("winsize")
            ),
            Linker.Option.firstVariadicArg(2), // address is actually variadic
            Linker.Option.captureCallState("errno") // inserts an argument on the call site to capture errno
    );


    try (var arena = Arena.ofConfined()) {
      var memorySegment = arena.allocate(WINSIZE_LAYOUT);
      Errno errno = new Errno(arena);
      int result = (int) ioctl.invokeExact(errno.storage, STDOUT_FILENO, TIOCGWINSZ, memorySegment);
      if (result == -1) {
        // ENOTTY: Inappropriate ioctl for device
        glibc_print_errno();
        throw new RuntimeException(STR."\{errno.get()}:  \{errno.message()}");
      }


      return new Winsize(memorySegment);
    }
  }

  public static void glibc_print_errno() throws Throwable {
    if (Objects.equals(System.getProperty("os.name"), "Mac OS X")) {
      return;
    }

    var printf = LINKER.downcallHandle(
            SYMBOL_LOOKUP.find("printf").orElseThrow(),
            FunctionDescriptor.of(
                    JAVA_INT,
                    ADDRESS
            )
    );


    try (var arena = Arena.ofConfined()) {
      @SuppressWarnings("unused")
      var unused = (int) printf.invokeExact(arena.allocateFrom("%m"));
    }
  }

  static class Errno {
    public static final MethodHandle strerror;

    static {
      Linker linker = Linker.nativeLinker();
      strerror = linker.downcallHandle(
              linker.defaultLookup().find("strerror").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    private final static StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
    private static final VarHandle errnoHandle = capturedStateLayout.varHandle(PathElement.groupElement("errno"));
    private final MemorySegment storage;

    Errno(Arena arena) {
      storage = arena.allocate(capturedStateLayout);
    }

    int get() {
      return (int) errnoHandle.get(storage, 0L);
    }

    String message() throws Throwable {
      return strerror(get());
    }

    // char *strerror(int errnum);
    private static String strerror(int errno) throws Throwable {
      // /* The error code set by various library functions.  */
      // extern int *__errno_location (void) __THROW __attribute_const__;
      // # define errno (*__errno_location ())
      return ((MemorySegment) strerror.invokeExact(errno)).reinterpret(Long.MAX_VALUE).getString(0);
    }

    @Override
    public String toString() {
      return STR."errno=\{get()}";
    }
  }
}
