/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.apache.tools.ant.taskdefs.ConditionTask
import org.gradle.api.internal.ConventionTask
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.process.internal.ExecException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths


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
    java.srcDirs(layout.buildDirectory.dir("generated/sources/jextract-blake3/java"))
    // resources.srcDirs("$buildDir/generated/sources/jextract/resources")
  }

  val syscall by creating {
    java.srcDirs(layout.buildDirectory.dir("generated/sources/jextract-syscall/java"))
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
abstract class JExtractTask @Inject constructor(
  // private val workerExecutor: WorkerExecutor, // TODO explore worker API
  private val objects: ObjectFactory,
  private val providers: ProviderFactory,
  private val layout: ProjectLayout,
  private val execOperations: ExecOperations,
) : DefaultTask() {
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

  @get:Input
  @get:Optional
  abstract val args: ListProperty<String>

  @get:InputFiles
  @get:Optional
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val argFile: RegularFileProperty

  @get:Input
  @get:Optional
  abstract val argFileContent: Property<String>

  @get:OutputDirectory
  abstract val targetPath: DirectoryProperty

  init {
    description = "Generate Java bindings from C headers using jextract"
    jextractBinaryPath.convention(getJExtractPath())
    targetPath.convention(layout.buildDirectory.dir("generated/sources/jextract/java"))
    // targetPath.convention(objectFactory.directoryProperty().fileValue(
    //   project.layout.buildDirectory.dir("/generated/sources/jextract/java")
    //   // project.sourceSets["jextract"].java.sourceDirectories.first()
    // ))
    outputs.dir(targetPath.get())
  }

  @TaskAction
  fun runJextract() {
    checkInputs()
    project.delete(outputs.files)

    val execResult = execOperations.exec {
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
      if (argFile.isPresent) {
        args("@${argFile.get().asFile.absolutePath}")
      } else if (argFileContent.isPresent) {
        val tmpArgFile = Files.createTempFile("", "").also {
          // delaying deletion to after the daemon stops to give a chance to look at the temporary file
          it.toFile().deleteOnExit()
        }
        Files.writeString(tmpArgFile, argFileContent.get())

        args("@${tmpArgFile.toAbsolutePath()}")
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


      isIgnoreExitValue = true // handled

      logger.info("Running jextract: {}", commandLine.joinToString(" "))
    }

    val status = execResult.exitValue
    when {
      status == 0 -> logger.info("jextract execution successful")
      status > 128 -> when (val signal = status - 128) {
        9 -> {
          logger.warn("jextract execution terminated with signal SIGKILL")
          if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX) {
            val jextractFolder = Paths.get(jextractBinaryPath.get()).toRealPath().resolve("../..").normalize().toAbsolutePath()
            logger.warn(
              """
              On macOS, this is likely due to the jextract binary being quarantined.
              You can fix this by running the following command:
                  sudo xattr -r -d com.apple.quarantine $jextractFolder
              """.trimIndent()
            )
          }
        }
        15 -> logger.warn("jextract execution terminated with signal SIGTERM")
        else -> logger.warn("jextract execution terminated with signal $signal")
      }

      else -> logger.warn("jextract execution failed with exit code $status")
    }

    execResult.assertNormalExitValue()

    if (execResult.exitValue != 0) {
      throw GradleException("jextract execution failed with exit code ${execResult.exitValue}")
    }
  }

  private fun checkInputs() {
    if (Files.notExists(Path.of(jextractBinaryPath.get()))) {
      throw InvalidUserCodeException(
        buildString {
          append(
            """
            jextract not found at ${jextractBinaryPath.get()}
            You can find releases for the current JDK on https://jdk.java.net/jextract/
            You can also build it from source at https://github.com/openjdk/jextract
            """.trimIndent()
          )

          if (DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX)
            append(
              """
              Also don't forget to run the following
                  sudo xattr -r -d com.apple.quarantine path/to/jextract/folder/
              """.trimIndent()
            )
        }
      )
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
    if (argFile.isPresent && argFileContent.isPresent) {
      throw InvalidUserCodeException("Use either 'argFile' or 'argFileContent'")
    }
    if (argFile.isPresent && !argFile.get().asFile.isFile) {
      throw InvalidUserCodeException("File does not exist: '${argFile.get()}'")
    }
  }

  private fun getJExtractPath(): String {
    val pathByProperty = project.findProperty("jextract")?.let {
      (it as String).replace("\$HOME", providers.systemProperty("user.home").get())
    }?.also {
      logger.info("Using `jextract` property: $it")
    }

    return pathByProperty ?: providers.environmentVariable("JEXTRACT")
      .filter(String::isNotBlank)
      .orNull
      ?.also {
        logger.info("Using `JEXTRACT` environment variable: $it")
      }
    ?: throw GradleException("jextract property or JEXTRACT environment variable not set")
  }
}


tasks.register<JExtractTask>("jextractBlake3") {
  headerClassName.set("blake3_h")
  targetPackage.set("blake3")
  targetPath.set(file(layout.buildDirectory.file("generated/sources/jextract-blake3/java")))
  headerPathIncludes.from(file("/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/"))
  headers.from(file("/Users/brice.dutheil/opensource/BLAKE3/c/blake3.h"))

  args.set(
    listOf(
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
  )
}

tasks.register<JExtractTask>("jextractSyscall") {
  headerClassName.set("syscall_h")
  targetPackage.set("unistd")
  targetPath.set(file(layout.buildDirectory.file("generated/sources/jextract-syscall/java")))
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
    --include-macro SYS_memfd_secret
    
    --include-function close
    --include-function ftruncate
    
    --include-function mmap
    --include-function munmap
    --include-macro PROT_READ
    --include-macro PROT_WRITE
    --include-macro MAP_SHARED
    
    --include-function strerror
    
    #### Since errno macro is not supported at this time, it is necessary
    #### to manually resolve errno, and include OS specific declarations.
    #### e.g. __errno_location is Linux specific
    --include-function __errno_location
    """.trimIndent()
  )

  // outputs.upToDateWhen { false }
}
tasks.compileJava.get().dependsOn("jextractBlake3", "jextractSyscall")