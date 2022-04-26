/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.a.first.contact;

import jdk.incubator.foreign.*;

public class FirstContact {
  public static void main(String[] args) throws Throwable {
    System.out.println("pid: " + c_getpid());
    c_printf("Hello C");
  }

  public static long c_printf(String str) throws Throwable {
    var printf = CLinker.systemCLinker()
                        .downcallHandle(
                                CLinker.systemCLinker().lookup("printf").get(),
                                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
                        );

    try (var scope = ResourceScope.newConfinedScope()) {
      var allocator = SegmentAllocator.nativeAllocator(scope);
      var memorySegment = allocator.allocateUtf8String(str);
      return (long) printf.invoke(memorySegment);
    }
  }

  public static long c_getpid() throws Throwable {
    var getpid = CLinker.systemCLinker()
                        .downcallHandle(
                                CLinker.systemCLinker().lookup("getpid").get(),
                                FunctionDescriptor.of(ValueLayout.JAVA_LONG)
                        );

    return (long) getpid.invokeExact();
  }
}
