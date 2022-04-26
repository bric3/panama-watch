/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.b.touchid;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SymbolLookup;

public class TouchId {

  public static void main(String[] args) throws Throwable {
//    System.load("path/to/libTouchIdDemoLib.dylib");
    System.loadLibrary("TouchIdDemoLib");

    // $ nm swift-library/build/lib/main/debug/libTouchIdDemoLib.dylib
    // ...
    // 0000000000001760 T _authenticate_user_touchid
    // ...
    var authenticate_user = CLinker.systemCLinker().downcallHandle(
            SymbolLookup.loaderLookup().lookup("authenticate_user_touchid").get(),
            FunctionDescriptor.ofVoid()
    );

    try (var scope = ResourceScope.newConfinedScope()) {
      authenticate_user.invoke();
    }
  }
}
