plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val useEmulators = providers.gradleProperty("useEmulators").orElse("false").get().toBoolean()
if (!useEmulators && file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "com.portfolio.choaezip"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.portfolio.choaezip"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "USE_EMULATORS", useEmulators.toString())
        buildConfigField("String", "EMULATOR_HOST", "\"${providers.gradleProperty("emulatorHost").orElse("10.0.2.2").get()}\"")
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst { check(useEmulators || file("google-services.json").exists()) {
        "Add app/google-services.json for Firebase, or explicitly use -PuseEmulators=true for local testing."
    } }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { check(!useEmulators && file("google-services.json").exists()) {
        "Release requires app/google-services.json and -PuseEmulators=false"
    } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Spark: implementation("com.google.firebase:firebase-functions")
    // Spark: implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Forward only transport/trust properties needed by Robolectric's verified SDK download.
tasks.withType<Test>().configureEach {
    systemProperty("sparkIntegration", providers.gradleProperty("sparkIntegration").orElse("false").get())
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("user.home", testHome.absolutePath)
    doFirst { testHome.mkdirs() }
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "http.nonProxyHosts", "javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
