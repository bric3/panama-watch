/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  id("panama.java-conventions")
}

val syscallSourcePath = project.configureJExtractSourceSet("syscall")

val jextractSyscall by tasks.registering(JExtractTask::class) {
  onlyIf {
    DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX
  }

  headerClassName.set("syscall_h")
  targetPackage.set("unistd")
  targetPath.set(file(syscallSourcePath))
  headerPathIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
  headerContent.set(
    """
    #include <errno.h>
    #include <unistd.h>
    #include <sys/syscall.h>
    #include <sys/mman.h>
    """.trimIndent()
  )

  argFileContent.set(
    """
    --include-function syscall
    --include-constant SYS_getpid
    --include-constant SYS_getuid
    --include-constant SYS_getgid
    --include-constant SYS_chmod
    --include-constant SYS_mkdir
        
    --include-function strerror

    
    --include-function __error
    
    #### Since errno macro is not supported at this time, it is necessary
    #### to manually resolve errno, and include OS specific declarations.
    #### e.g. __errno_location is Linux specific
    --include-function __errno_location
    """.trimIndent()
  )

  // outputs.upToDateWhen { false }
}
tasks.compileJava.get().dependsOn(jextractSyscall)