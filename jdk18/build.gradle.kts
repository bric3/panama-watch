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
import java.nio.file.Files
import java.nio.file.Path


plugins {
  java
}

///             ___
///   | _.   _.  | _  _ | _|_  _.o._
/// \_|(_|\/(_|  |(_)(_)|(_| |(_||| |
///////////////////////////////////////////////////////////////////////


java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(18))
  }
}


// Due to https://github.com/gradle/gradle/issues/18426, tasks are not declared in the TaskContainerScope
tasks.withType<JavaExec>().configureEach {
  dependsOn(tasks.compileJava, "swiftLib") // for IntelliJ run main class
  group = "class-with-main"
  classpath(sourceSets.main.get().runtimeClasspath)

  // Need to set the toolchain https://github.com/gradle/gradle/issues/16791
  javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
  jvmArgs(
    "-ea",
    "--enable-native-access=ALL-UNNAMED",
    "--add-modules=jdk.incubator.foreign",
    "--enable-preview"
  )

  environment = mapOf(
    "JAVA_LIBRARY_PATH" to sourceSets.main.get().output.resourcesDir!! // for IntelliJ run main class
    // "JAVA_LIBRARY_PATH" to ".:/usr/local/lib"
  )
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(18)
  options.compilerArgs = listOf(
    "--add-modules=jdk.incubator.foreign",
    "--enable-preview",
    "-Xlint:preview"
  )
}

/// ___          ___
///  | _     _|_  |  _|
///  |(_)|_|(_| |_|_(_|
///////////////////////////////////////////////////////////////////////

// See https://docs.gradle.org/current/userguide/cross_project_publications.html
val os = DefaultNativePlatform.getCurrentOperatingSystem()
if (os.isMacOsX) {
  tasks.register("swiftLib") {
    dependsOn(":touchid-swift-lib:assembleReleaseSharedMacos")
    doLast {
      val sharedLib = tasks.getByPath(":touchid-swift-lib:linkReleaseSharedMacos").outputs.files.filter { it.isFile }
      copy {
        from(sharedLib.asFileTree)
        into(sourceSets.main.get().output.resourcesDir!!)
      }
    }
  }

  // JAVA_LIBRARY_PATH=jdk18/build/resources/main/ java --enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.foreign -cp jdk18/build/classes/java/main sandbox.TouchId
  tasks.register<JavaExec>("touchId") {
    dependsOn(tasks.compileJava, "swiftLib")
    mainClass.set("sandbox.TouchId")
    environment = mapOf(
      "JAVA_LIBRARY_PATH" to sourceSets.main.get().output.resourcesDir!!
      //          "JAVA_LIBRARY_PATH" to ".:/usr/local/lib"
    )
  }
} else {
  logger.warn("Touch Id example disabled as biometerics are not qvilable on other platforms")
  // throw GradleException("Swift compilation is only working on macOS and not configured on Linux")
}

///  _          _ _
/// |_)|  /\ |/|_ _)
/// |_)|_/--\|\|_ _)
///////////////////////////////////////////////////////////////////////

sourceSets {
  val blake3 by creating {
    java.srcDirs("$buildDir/generated/sources/jextract-blake3/java")
    // resources.srcDirs("$buildDir/generated/sources/jextract/resources")
  }

  val syscall by creating {
    java.srcDirs("$buildDir/generated/sources/jextract-syscall/java")
    // resources.srcDirs("$buildDir/generated/sources/jextract/resources")
  }

  @Suppress("UNUSED_VARIABLE")
  val main by getting {
    java.srcDirs(blake3.java.srcDirs)
    java.srcDirs(syscall.java.srcDirs)
    // resources.srcDirs(blake3.resources.srcDirs)
    // resources.srcDirs(syscall.resources.srcDirs)
    // compileClasspath += sourceSets["jextract"].output
    // runtimeClasspath += sourceSets["jextract"].output
  }
}


@CacheableTask
abstract class JextractTask : AbstractExecTask<JextractTask>(JextractTask::class.java) {
  @get:Input
  abstract val jextractBinaryPath: Property<String>

  @get:Input
  @get:Optional
  abstract val targetPackage: Property<String>

  @get:Input
  @get:Optional
  abstract val libraryName: Property<String>

  @get:Input
  @get:Optional
  abstract val headerClassName: Property<String>

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val headerPathIncludes: ConfigurableFileCollection

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val headers: ConfigurableFileCollection

  @get:Input
  @get:Optional
  abstract val headerContent: Property<String>

  @get:OutputDirectory
  abstract val targetPath: DirectoryProperty

  init {
    jextractBinaryPath.convention(getJextractPath())
    targetPath.convention(project.layout.buildDirectory.dir("generated/sources/jextract/java"))
    // targetPath.convention(objectFactory.directoryProperty().fileValue(
    //   project.layout.buildDirectory.dir("/generated/sources/jextract/java")
    //   // project.sourceSets["jextract"].java.sourceDirectories.first()
    // ))
    outputs.dir(targetPath.get())
  }

  @TaskAction
  override fun exec() {
    checkInputs()

    workingDir = project.projectDir
    executable = jextractBinaryPath.get()

    args(
      "--source",
      "--output", targetPath.get(),
    )
    if (targetPackage.isPresent) {
      args("--target-package", targetPackage.get())
    }
    if (headerClassName.isPresent) {
      args("--header-class-name", headerClassName.get())
    }
    if (libraryName.isPresent) {
      args("-l", libraryName.get())
    }
    headerPathIncludes.files.forEach { headerDirectory ->
      args("-I", headerDirectory)
    }
    /* resolved via argument provider */
    argumentProviders.add {
      val tmpHeader = Files.createTempFile("", ".h").also {
        // delaying deletion to after the daemon stops to give a chance to look at the temporary file
        it.toFile().deleteOnExit()
      }

      Files.writeString(
        tmpHeader,
        headerContent.getOrElse(buildString {
          headers.files.forEach { header ->
            append("""#include "${header}"\n""")
          }
        })
      )
      listOf(tmpHeader.toAbsolutePath().toString())
    }


    try {
      project.delete(outputs.files)
      super.exec()
    } catch (e: Exception) {
      throw GradleException("jextract execution failed", e)
    }
  }

  private fun checkInputs() {
    if (Files.notExists(Path.of(jextractBinaryPath.get()))) {
      throw InvalidUserCodeException("jextract not found at ${jextractBinaryPath.get()}")
    }
    headerPathIncludes.files.forEach { includedDirectory ->
      if (!includedDirectory.isDirectory) {
        throw InvalidUserCodeException("Not a header directory: '$includedDirectory'")
      }
    }
    if (headers.files.size > 0 && headerContent.isPresent) {
      throw InvalidUserCodeException("Use either 'headers' or 'headerContent'")
    }
    headers.files.forEach { header ->
      if (!header.isFile) {
        throw InvalidUserCodeException("Not a header directory: '$header'")
      }
    }
  }

  private fun getJextractPath(): String? {
    project.findProperty("jextract").let {
      if (it != null) {
        return (it as String).replace("\$HOME", System.getProperty("user.home"))
      }

      val jextractFromEnv = System.getenv("JEXTRACT")
      if (jextractFromEnv.isNotBlank()) {
        return jextractFromEnv
      }
      throw GradleException("jextract property or JEXTRACT environment variable not set")
    }
  }

}


tasks.register<JextractTask>("jextractBlake3") {
  headerClassName.set("blake3_h")
  targetPackage.set("blake3")
  targetPath.set(file("$buildDir/generated/sources/jextract-blake3/java"))
  headerPathIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
  headers.from(file("/Users/brice.dutheil/opensource/BLAKE3/c/blake3.h"))

  args(
    "--include-typedef", "blake3_chunk_state",
    "--include-typedef", "blake3_hasher",
    "--include-macro", "BLAKE3_BLOCK_LEN",
    "--include-macro", "BLAKE3_CHUNK_LEN",
    "--include-macro", "BLAKE3_KEY_LEN",
    "--include-macro", "BLAKE3_MAX_DEPTH",
    "--include-macro", "BLAKE3_OUT_LEN",
    "--include-macro", "BLAKE3_VERSION_STRING",
    "--include-function", "blake3_hasher_finalize",
    "--include-function", "blake3_hasher_finalize_seek",
    "--include-function", "blake3_hasher_init",
    "--include-function", "blake3_hasher_init_derive_key",
    "--include-function", "blake3_hasher_init_derive_key_raw",
    "--include-function", "blake3_hasher_init_keyed",
    "--include-function", "blake3_hasher_reset",
    "--include-function", "blake3_hasher_update",
    "--include-function", "blake3_version",
  )
}

tasks.register<JextractTask>("jextractSyscall") {
  headerClassName.set("syscall_h")
  targetPackage.set("unistd")
  targetPath.set(file("$buildDir/generated/sources/jextract-syscall/java"))
  headerPathIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
  headerContent.set(
    """
    #include <errno.h>
    #include <unistd.h>
    #include <sys/syscall.h>
    #include <sys/mman.h>
    """.trimIndent()
  )

  // args(
  //   "--dump-includes", "unistd-conf"
  // )
}
tasks.compileJava.get().dependsOn("jextractBlake3", "jextractSyscall")