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
// DefaultFeature so Windows Installer removes the entries on uninstall. The Compose task always passes its own
// (empty on Windows) --resource-dir after any extra argument, so the second run reuses Compose's argument file and
// appends --resource-dir last; jpackage keeps the last value. The template comes from the packaging JDK (jrt image),
// so it matches that jpackage exactly.
tasks.withType<AbstractJPackageTask>().matching { it.name == "packageMsi" }.configureEach {
    val packagingJdk = javaHome
    val wix = wixToolsetDir
    val msiDir = destinationDir
    val argsFile = layout.buildDirectory.file("compose/tmp/packageMsi.args.txt")
    val openWith = layout.projectDirectory.file("msi/open-with.wxs")
    val resources = layout.buildDirectory.dir("msi-resources")
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
                .replaceAfterLast("</Wix>", "")
                .removeSuffix("</Wix>") + fragment + "\n</Wix>\n"
        val resourceDir = resources.get().asFile.apply { deleteRecursively() }
        resourceDir.mkdirs()
        resourceDir.resolve("main.wxs").writeText(main)

        val out = msiDir.get().asFile
        out.listFiles { f -> f.extension == "msi" }?.forEach { it.delete() }
        val jpackage = File(home, "bin/jpackage.exe")
        val process =
            ProcessBuilder(jpackage.path, "@${argsFile.get().asFile.path}", "--resource-dir", resourceDir.path)
                .inheritIO()
                .apply {
                    val path = environment()["PATH"].orEmpty()
                    environment()["PATH"] = wix.get().asFile.path + File.pathSeparator + path
                }.start()
        check(process.waitFor() == 0) { "jpackage (MSI with Open with entries) failed" }
        check(out.listFiles { f -> f.extension == "msi" }.orEmpty().isNotEmpty()) { "no MSI in $out" }
        val entries = Regex("OpenWithProgids\" Name=").findAll(wxs).count()
        println("== MSI linked again with $entries Open with entries (msi/open-with.wxs)")
    }
}
