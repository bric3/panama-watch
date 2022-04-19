/*
 * MIT License
 *
 * Copyright (c) 2021 Brice Dutheil <brice.dutheil@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
  val jextract by creating {
    java.srcDirs("$buildDir/generated/sources/jextract/java")
    resources.srcDirs("$buildDir/generated/sources/jextract/resources")
  }

  @Suppress("UNUSED_VARIABLE")
  val main by getting {
    java.srcDirs(jextract.java.srcDirs)
    resources.srcDirs(jextract.resources.srcDirs)
//    compileClasspath += sourceSets["jextract"].output
//    runtimeClasspath += sourceSets["jextract"].output
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
  abstract val headerIncludes: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val headers: ConfigurableFileCollection

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
    if (Files.notExists(Path.of(jextractBinaryPath.get()))) {
      throw InvalidUserCodeException("jextract not found at ${jextractBinaryPath.get()}")
    }

    headerIncludes.files.forEach { includedDirectory ->
      if (!includedDirectory.isDirectory) {
        throw InvalidUserCodeException("Not a header directory: '$includedDirectory'")
      }
    }
    headers.files.forEach { header ->
      if (!header.isFile) {
        throw InvalidUserCodeException("Not a header directory: '$header'")
      }
    }

    workingDir = project.projectDir
    executable = jextractBinaryPath.get()

    args(
      "--source",
      "-d", targetPath.get(),
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
    headerIncludes.files.forEach { headerDirectory ->
      args("-I", headerDirectory)
    }
    /* resolved via argument provider */
    argumentProviders.add {
      val tmpHeader = Files.createTempFile("", ".h")

      Files.writeString(
        tmpHeader,
        buildString {
          headers.files.forEach { header ->
            append("#include <${header}>\n")
          }
        }
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
  headerIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
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