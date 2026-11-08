plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meyouww.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meyouww.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Release APKs for phones only carry ARM code (-Pmeyouww.abis=arm64-v8a,armeabi-v7a);
        // CI's emulator build keeps every ABI.
        (findProperty("meyouww.abis") as String?)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { abis ->
            ndk { abiFilters += abis }
        }
    }

    // libsignal and SQLCipher ship their native libraries with debug symbols (hundreds of MB).
    // Stripping needs an NDK; use the one the build machine has (GitHub runners set these).
    // AGP insists that ndkVersion matches the NDK at ndkPath, so read it from there.
    (System.getenv("ANDROID_NDK_LATEST_HOME") ?: System.getenv("ANDROID_NDK_HOME"))
        ?.let { file(it) }
        ?.takeIf { it.isDirectory }
        ?.let { ndk ->
            val revision = ndk.resolve("source.properties").takeIf { it.isFile }?.readLines()
                ?.firstOrNull { it.startsWith("Pkg.Revision") }?.substringAfter('=')?.trim()
            if (revision != null) {
                ndkPath = ndk.path
                ndkVersion = revision
            }
        }

    packaging {
        jniLibs {
            // libsignal's test-only native library is never used by the app.
            excludes += "**/libsignal_jni_testing.so"
        }
        resources {
            // libsignal-client (a dependency of libsignal-android) carries its desktop
            // libraries for macOS, Windows and Linux as Java resources: useless on a phone.
            excludes += listOf("**/*.dylib", "**/*.dll", "/libsignal_jni*.so", "/signal_jni*")
        }
    }

    signingConfigs {
        // A shared debug key (password "android", debug-only, not a secret) so every CI build
        // installs over the previous one instead of needing an uninstall.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing comes from environment variables only (see docs/RELEASE.md).
        create("release") {
            val storePath = System.getenv("MEYOUWW_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("MEYOUWW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MEYOUWW_KEY_ALIAS")
                keyPassword = System.getenv("MEYOUWW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Same package as release so one Firebase Android app covers both builds.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("MEYOUWW_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // libsignal uses newer Java APIs than Android 8 has.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/previous-compilation-data.bin")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:friends"))
    implementation(project(":feature:inbox"))
    implementation(project(":feature:studio"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:alarms"))
    implementation(project(":core:alarm"))
    implementation(project(":core:chat"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
