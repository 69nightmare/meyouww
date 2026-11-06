import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Public client settings for the backend. Order: environment variable, then
// local.properties (your computer), then config/backend.properties (committed).
// These values ship inside every APK; server secrets never appear here.
fun loadProperties(path: String) = Properties().apply {
    val file = rootProject.file(path)
    if (file.exists()) file.inputStream().use { load(it) }
}
val localProperties = loadProperties("local.properties")
val committedProperties = loadProperties("config/backend.properties")

fun config(key: String): String =
    (System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(key)
        ?: committedProperties.getProperty(key)
        ?: "").trim().replace("\"", "")

android {
    namespace = "com.meyouww.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", "\"${config("MEYOUWW_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${config("MEYOUWW_SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${config("MEYOUWW_FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${config("MEYOUWW_FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${config("MEYOUWW_FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${config("MEYOUWW_FIREBASE_SENDER_ID")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core:model"))

    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    api(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.tink.android)
    implementation(libs.play.services.base)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
