plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Hilt's generated code sometimes went missing in CI ("Cannot load class Hilt_...",
// "ChatDatabase cannot be provided"): KSP output restored from the build cache didn't
// always match the compiled classes. Always run KSP fresh; it takes seconds.
subprojects {
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
        outputs.cacheIf { false }
    }
}
