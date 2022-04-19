import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

rootProject.name = "panama-watch"
include("jdk18")

val os = DefaultNativePlatform.getCurrentOperatingSystem()
if (os.isMacOsX) {
    include("touchid-swift-lib")
}
