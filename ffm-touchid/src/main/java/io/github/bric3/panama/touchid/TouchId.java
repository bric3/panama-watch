/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.panama.touchid;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;

public class TouchId {

  public static void main(String[] args) throws Throwable {
//    System.load("path/to/libTouchIdDemoLib.dylib");
    System.loadLibrary("TouchIdDemoLib");

    // $ nm swift-library/build/lib/main/debug/libTouchIdDemoLib.dylib
    // ...
    // 0000000000001760 T _authenticate_user_touchid
    // ...
    var authenticate_user = Linker.nativeLinker().downcallHandle(
            SymbolLookup.loaderLookup().find("authenticate_user_touchid").orElseThrow(),
            FunctionDescriptor.ofVoid()
    );

    authenticate_user.invoke();
  }
}
