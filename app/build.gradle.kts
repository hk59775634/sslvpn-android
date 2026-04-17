import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val openConnectBootstrapVersion = "fdroid-net.openconnect_vpn.android-1120-v1"

android {
    namespace = "com.sslvpn.android"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.sslvpn.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
        // true = placeholder TUN only (no real AnyConnect). false = libopenconnect from bootstrap task.
        buildConfigField("boolean", "USE_MOCK_ENGINE", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    // Registrable domain (eTLD+1) for site → node.<apex> profile fetch, aligned with sslcon-client.
    implementation("com.google.guava:guava:33.3.1-android")
}

tasks.register("syncOpenConnectBootstrap") {
    group = "openconnect"
    description =
        "Downloads the F-Droid OpenConnect APK and extracts libopenconnect.so, libstoken.so, and curl-bin into src/main (LGPL; see legal/bootstrap-source.txt)."
    val marker = layout.projectDirectory.file("src/main/jniLibs/.bootstrap-${openConnectBootstrapVersion}")
    outputs.file(marker)

    doLast {
        val apk = layout.buildDirectory.file("tmp/openconnect-bootstrap/base.apk").get().asFile
        apk.parentFile?.mkdirs()
        URI("https://f-droid.org/repo/net.openconnect_vpn.android_1120.apk").toURL().openStream()
            .use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }

        ZipInputStream(apk.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    when {
                        name.startsWith("lib/") && name.endsWith(".so") -> {
                            val rel = name.removePrefix("lib/")
                            val out = layout.projectDirectory.file("src/main/jniLibs/$rel").asFile
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zis.copyTo(it) }
                        }
                        name.startsWith("assets/raw/") && name.endsWith("curl-bin") -> {
                            val idx = name.indexOf("assets/")
                            val rel = name.substring(idx + "assets/".length)
                            val out = layout.projectDirectory.file("src/main/assets/$rel").asFile
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zis.copyTo(it) }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val armeabi = layout.projectDirectory.dir("src/main/jniLibs/armeabi").asFile
        val v7 = layout.projectDirectory.dir("src/main/jniLibs/armeabi-v7a").asFile.apply { mkdirs() }
        File(armeabi, "libopenconnect.so").takeIf { it.exists() }
            ?.copyTo(File(v7, "libopenconnect.so"), overwrite = true)
        File(armeabi, "libstoken.so").takeIf { it.exists() }
            ?.copyTo(File(v7, "libstoken.so"), overwrite = true)

        val armeabiCurl = layout.projectDirectory.file("src/main/assets/raw/armeabi/curl-bin").asFile
        val v7curl = layout.projectDirectory.dir("src/main/assets/raw/armeabi-v7a").asFile.apply { mkdirs() }
        armeabiCurl.takeIf { it.exists() }?.copyTo(File(v7curl, "curl-bin"), overwrite = true)

        marker.asFile.parentFile?.mkdirs()
        marker.asFile.writeText(openConnectBootstrapVersion)
    }
}

tasks.named("preBuild").configure {
    dependsOn("syncOpenConnectBootstrap")
}
