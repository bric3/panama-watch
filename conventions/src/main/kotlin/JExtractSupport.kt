/*
 * panama-watch
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.ABSOLUTE
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject


fun Project.configureJExtractSourceSet(name: String): Provider<Directory> {
  val srcPath = layout.buildDirectory.dir("generated/sources/jextract-$name/java")

  extensions.configure(SourceSetContainer::class.java) {
    val jextractSourceSet = create("jextract") {
      java.srcDirs(srcPath)
    }

    afterEvaluate {
      tasks.getByName("compileJextractJava") {
        tasks.filterIsInstance<JExtractTask>().forEach { jextractTask ->
          mustRunAfter(jextractTask)
        }
      }
    }

    getByName("main") {
      // java.srcDirs(jextractSourceSet.java.srcDirs)

      compileClasspath += jextractSourceSet.output
      runtimeClasspath += jextractSourceSet.output
      // resources.srcDirs(syscall.resources.srcDirs)
      // compileClasspath += sourceSets["jextract"].output
      // runtimeClasspath += sourceSets["jextract"].output
    }
  }

  return srcPath
}

private const val JEXTRACT_BINARY_RELATIVE_PATH = "/bin/jextract"
private const val JEXTRACT_HOME_ENV_NAME = "JEXTRACT_HOME"
private const val JEXTRACT_HOME_PROPERTY_NAME = "jextract_home"

@CacheableTask
abstract class JExtractTask @Inject constructor(
  // private val workerExecutor: WorkerExecutor, // TODO explore worker API
  private val objects: ObjectFactory,
  private val providers: ProviderFactory,
  private val layout: ProjectLayout,
  private val execOperations: ExecOperations,
) : DefaultTask() {
  /**
   * Sets the path to the `jextract` binary home folder.
   */
  @get:Input
  abstract val jextractHome: Property<String>

  /**
   * Sets the library name for this header, e.g. will add `--library mylib` argument.
   *
   * TODO multiple library names are supported
   */
  @get:Input
  @get:Optional
  abstract val libraryName: Property<String>

  /**
   * Sets the package name for this header, e.g. will add `--target-package my.pkg` argument.
   */
  @get:Input
  @get:Optional
  abstract val targetPackage: Property<String>

  /**
   * Simple name class for this [headers], e.g. will add `--header-class-name MyLibrary`.
   */
  @get:Input
  @get:Optional
  abstract val headerClassName: Property<String>

  /**
   * Includes header directories that are needed to parse [headers],
   * will add `--include-dir path/to/include`.
   */
  @get:InputFiles
  @get:Optional
  @get:PathSensitive(ABSOLUTE)
  abstract val headerPathIncludes: ConfigurableFileCollection

  /**
   * Sets the header files that will be parsed.
   *
   * If a single file is passed, the command line will be equivalent to
   * ```shell
   * $ jextract ... theHeader.h
   * ```
   *
   * However, if multiple files are passed, a generated header file
   * will be created, that will import each of these headers.
   * ```shell
   * $ <<-TMP_HEADER_FILE > tmp.h
   * #include "path/to/header1.h"
   * #include "path/to/header1.h"
   * TMP_HEADER_FILE
   * ```
   *
   * And then passed to jextract as
   * ```shell
   * $ jextract ... tmp.h
   * ```
   *
   * If [headerContent] is set, this property is ignored.
   */
  @get:InputFiles
  @get:Optional
  @get:PathSensitive(ABSOLUTE)
  abstract val headers: ConfigurableFileCollection

  /**
   * Sets the content of the header file that will be parsed.
   *
   * The content should be of the form:
   *
   * ```c
   * #include "path/to/header1.h"
   * #include "path/to/header1.h"
   * ```
   */
  @get:Input
  @get:Optional
  abstract val headerContent: Property<String>

  /**
   * Lists of arguments to pass to `jextract`.
   *
   * Can be replaced by [argFile] or [argFileContent].
   * If both are set, [args] is applied before [argFile] or [argFileContent].
   * @see argFile
   * @see argFileContent
   */
  @get:Input
  @get:Optional
  abstract val args: ListProperty<String>

  /**
   * Pass an arg file on the cli, e.g. `@argFile.txt`.
   * @see argFileContent
   */
  @get:InputFiles
  @get:Optional
  @get:PathSensitive(ABSOLUTE)
  abstract val argFile: RegularFileProperty

  /**
   * Use the [argFileContent] content as the arg file, and pass it on the cli, e.g. `@tmpArgFile.txt`.
   * Ignored if [argFile] is set.
   * @see argFile
   */
  @get:Input
  @get:Optional
  abstract val argFileContent: Property<String>

  /**
   * Sets the location of the generated files, e.g. will add `--output location` argument.
   *
   * By default, in `build/generated/sources/jextract/java`.
   */
  @get:OutputDirectory
  abstract val targetPath: DirectoryProperty

  init {
    description = "Generate Java bindings from C headers using jextract"
    jextractHome.convention(getJExtractPathHome())
    // TODO setup convention sourceSet if used (configureJExtractSourceSet)
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
      executable = jextractHome.map { it + JEXTRACT_BINARY_RELATIVE_PATH }.get()

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
        args("--library", libraryName.get())
      }
      headerPathIncludes.files.forEach { headerDirectory ->
        args("--include-dir", headerDirectory)
      }
      args(this@JExtractTask.args.get())
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
            val jextractHome =
              Paths.get(jextractHome.get()).toRealPath().resolve("../..").normalize().toAbsolutePath()
            logger.warn(
              """
              On macOS, this is likely due to the jextract binary being quarantined.
              You can fix this by running the following command:
                  sudo xattr -r -d com.apple.quarantine $jextractHome
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
    if (Files.notExists(Paths.get(jextractHome.get()))) {
      throw InvalidUserCodeException(
        buildString {
          append(
            """
            jextract not found at ${jextractHome.get()}$JEXTRACT_BINARY_RELATIVE_PATH
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

  private fun getJExtractPathHome(): String {
    val pathByProperty = project.findProperty(JEXTRACT_HOME_PROPERTY_NAME)?.let {
      (it as String).replace("\$HOME", providers.systemProperty("user.home").get())
    }?.also {
      logger.info("Using `$JEXTRACT_HOME_PROPERTY_NAME` property: $it")
    }

    return pathByProperty ?: providers.environmentVariable(JEXTRACT_HOME_ENV_NAME)
      .filter(String::isNotBlank)
      .orNull
      ?.also {
        logger.info("Using `$JEXTRACT_HOME_ENV_NAME` environment variable: $it")
      }
    ?: throw GradleException("$JEXTRACT_HOME_PROPERTY_NAME property or $JEXTRACT_HOME_ENV_NAME environment variable not set")
  }
}
