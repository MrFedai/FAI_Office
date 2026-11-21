// Compose Multiplatform shell: screens, navigation, design system (commonMain) and the desktop and Web entry
// points. The Android entry point lives in :androidApp (AGP 9 keeps com.android.application out of KMP modules).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "org.faioffice.app"
        // Compose resources (fonts) must reach the Android assets of the KMP library.
        androidResources.enable = true
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "fai-office.js" } }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // File hub (P3.4): opened files and added folders flow to the screens off the main thread.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        named("desktopMain") {
            dependencies { implementation(compose.desktop.currentOs) }
        }
        // Contrast test and token preview screenshots (ImageComposeScene) run on the desktop JVM only.
        named("desktopTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Screenshots of the desktop tests: app/build/bakis/<theme>_<size>_<screen>.png.
val bakisDir = layout.buildDirectory.dir("bakis")
tasks.withType<Test>().configureEach {
    systemProperty("bakis.dir", bakisDir.get().asFile.absolutePath)
}

// Bundled fonts (composeResources/font, DESIGN.md section 4); Res stays internal to :app.
compose.resources {
    packageOfResClass = "org.faioffice.app.resources"
    publicResClass = false
}

compose.desktop {
    application {
        mainClass = "org.faioffice.app.MainKt"
        // AppCDS (P2.8b, risk R7): the installed app dumps a class data sharing archive on its first start
        // (CdsArchive.kt, from the class list in app/cds/common, written by ci/cds-classlist.sh) and maps it from
        // the next start on. Missing, empty or stale archive: the JVM silently starts without it.
        jvmArgs += listOf("-XX:SharedArchiveFile=\$APPDIR/resources/fai.jsa", "-Dfai.cds=\$APPDIR/resources/fai.jsa")
        // jpackage builds only for the OS it runs on: AppImage on Linux, MSI on Windows (CI windows job,
        // ci/windows.sh; WiX is fetched by the Compose plugin as a build tool and does not enter the package).
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "fai-office"
            packageVersion = "0.2.0"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.instrument", "jdk.unsupported")
            // Into <app>/resources: the class list, and empty fai.jsa / fai.jsa.stamp placeholders so the archive
            // the app writes there belongs to the package (an MSI uninstall removes it; an update resets it).
            appResourcesRootDir.set(layout.projectDirectory.dir("cds"))
            windows {
                menuGroup = "FAI Office"
                // Per-user install: no administrator rights; the same UUID lets a newer MSI replace an older one.
                perUserInstall = true
                dirChooser = true
                shortcut = true
                upgradeUuid = "6f1d8a52-3c7e-4b9a-9e41-0d2b7c5f8a13"
            }
        }
        buildTypes.release.proguard { isEnabled.set(true) }
    }
}

// Windows "Open with" (P3.5): jpackage's own file associations write the default value of HKCU\Software\Classes\.ext,
// a default-app claim, so they stay unused. Instead the MSI is linked a second time with jpackage's own main.wxs plus
// the Fragment of msi/open-with.wxs (OpenWithProgids entries only; WindowsOpenWithTest checks it), referenced from
// DefaultFeature so Windows Installer removes the entries on uninstall. The template comes from the packaging JDK
// (jrt image), so it matches that jpackage exactly.
// One link is not possible: the Compose task empties its own --resource-dir (build/compose/tmp/resources) inside its
// action and passes it after any extra argument, and jpackage keeps the last value. So the second run reuses Compose's
// argument file (build/compose/tmp/packageMsi.args.txt, one argument per line) with --resource-dir replaced, plus
// --verbose and a kept --temp. Everything goes to build/msi-open-with: resources/main.wxs, temp/ (jpackage's config
// and WiX objects), jpackage.log (jpackage and WiX output). A failure quotes the log's error lines and tail and copies
// the files into build/ci/msi-open-with, which the CI job uploads (ci-logs-windows).
tasks.withType<AbstractJPackageTask>().matching { it.name == "packageMsi" }.configureEach {
    val packagingJdk = javaHome
    val wix = wixToolsetDir
    val msiDir = destinationDir
    val composeArgs = layout.buildDirectory.file("compose/tmp/packageMsi.args.txt")
    val composeResources = layout.buildDirectory.dir("compose/tmp/resources")
    val openWith = layout.projectDirectory.file("msi/open-with.wxs")
    val work = layout.buildDirectory.dir("msi-open-with")
    val ciLogs = rootProject.layout.buildDirectory.dir("ci/msi-open-with")
    inputs.file(openWith)
    doLast {
        val home = packagingJdk.get()
        val template =
            FileSystems
                .newFileSystem(URI.create("jrt:/"), mapOf("java.home" to home))
                .use { jrt ->
                    Files.readString(
                        jrt.getPath("/modules/jdk.jpackage/jdk/jpackage/internal/resources/main.wxs"),
                    )
                }
        val anchor = "<ComponentGroupRef Id=\"FileAssociations\"/>"
        check(template.contains(anchor) && template.contains("</Wix>")) {
            "jpackage main.wxs of $home changed: no $anchor or </Wix>; update the Open with step in app/build.gradle.kts"
        }
        val wxs = openWith.asFile.readText()
        val fragment = wxs.substring(wxs.indexOf("<Fragment>"), wxs.indexOf("</Fragment>") + "</Fragment>".length)
        val main =
            template
                .replace(anchor, "$anchor\n      <ComponentGroupRef Id=\"FaiOpenWith\"/>")
                .substringBeforeLast("</Wix>") + fragment + "\n</Wix>\n"

        // jpackage refuses a --temp folder that is not empty, so the whole work folder starts fresh.
        val workDir = work.get().asFile.apply { deleteRecursively() }
        val resourceDir = workDir.resolve("resources").apply { mkdirs() }
        composeResources
            .get()
            .asFile
            .listFiles()
            ?.forEach { it.copyRecursively(resourceDir.resolve(it.name)) }
        resourceDir.resolve("main.wxs").writeText(main)
        val tempDir = workDir.resolve("temp")
        val log = workDir.resolve("jpackage.log")

        // Same form as Compose's own values: quoted, backslashes doubled (the argument file escapes them).
        fun quoted(f: File) = "\"" + f.absolutePath.replace("\\", "\\\\") + "\""
        val composeLines =
            composeArgs
                .get()
                .asFile
                .readLines()
                .filter { it.isNotBlank() }
        // Compose's --resource-dir and any --temp go with their values, --verbose too: all three come back below.
        val withValue = setOf("--resource-dir", "--temp")
        val args =
            composeLines.filterIndexed { n, line ->
                line.trim() !in withValue + "--verbose" && composeLines.getOrNull(n - 1)?.trim() !in withValue
            } + listOf("--resource-dir", quoted(resourceDir), "--temp", quoted(tempDir), "--verbose")
        val argsFile = workDir.resolve("packageMsi.args.txt").apply { writeText(args.joinToString("\n")) }

        val out = msiDir.get().asFile
        out.listFiles { f -> f.extension == "msi" }.orEmpty().forEach { msi ->
            // A virus scan of the fresh MSI can hold it for a moment; an MSI left there would hide a failed link.
            var tries = 0
            while (msi.exists() && !msi.delete()) {
                check(++tries < 40) { "cannot delete the first MSI $msi (still open in another process?)" }
                Thread.sleep(250)
            }
        }
        val jpackage = File(home, "bin/jpackage.exe")
        val process =
            ProcessBuilder(jpackage.path, "@${argsFile.path}")
                .redirectErrorStream(true)
                .redirectOutput(log)
                .apply {
                    // This map is case-sensitive while Windows names the variable Path: extend the existing entry.
                    val env = environment()
                    val path = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
                    env[path] = wix.get().asFile.path + File.pathSeparator + env[path].orEmpty()
                }.start()
        val code = process.waitFor()
        val msis = out.listFiles { f -> f.extension == "msi" }.orEmpty()
        if (code != 0 || msis.isEmpty()) {
            val keep = ciLogs.get().asFile.apply { deleteRecursively() }
            log.takeIf { it.exists() }?.copyTo(keep.resolve(log.name))
            argsFile.copyTo(keep.resolve(argsFile.name))
            resourceDir.resolve("main.wxs").copyTo(keep.resolve("resources/main.wxs"))
            tempDir.resolve("config").takeIf { it.isDirectory }?.copyRecursively(keep.resolve("config"))
            val lines = log.takeIf { it.exists() }?.readLines().orEmpty()
            val marker = Regex("error|exception|LGHT\\d|CNDL\\d|ICE\\d|exited with", RegexOption.IGNORE_CASE)
            throw GradleException(
                buildString {
                    appendLine("jpackage (MSI with Open with entries) exited with $code, ${msis.size} MSI in $out")
                    appendLine("full output: $log (also in $keep with main.wxs, args and jpackage's config)")
                    appendLine("-- error lines (last 40):")
                    lines.filter { marker.containsMatchIn(it) }.takeLast(40).forEach { appendLine(it) }
                    appendLine("-- last 80 lines:")
                    lines.takeLast(80).forEach { appendLine(it) }
                },
            )
        }
        val entries = Regex("OpenWithProgids\" Name=").findAll(wxs).count()
        println("== MSI linked again with $entries Open with entries (msi/open-with.wxs; log: $log)")
    }
}
