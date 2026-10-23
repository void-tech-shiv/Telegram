import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is completely optional for local builds and public CI.
// Add app/google-services.json locally to activate the push transport.
if (file("google-services.json").isFile) apply(plugin = "com.google.gms.google-services")

val privateProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val telegramApiId = privateProps.getProperty("telegram.apiId")?.trim()?.toIntOrNull() ?: 0
val telegramApiHash = privateProps.getProperty("telegram.apiHash")?.trim().orEmpty()

// Android Studio's Run button does not inherit an earlier command-line -P flag.
// Allow an explicit, private local.properties opt-in to persist across IDE builds.
// Never enable experimental JNI voice/video calling in release APKs.
val experimentalNativeCalls = (
    providers.gradleProperty("glacia.experimentalNativeCalls").orNull
        ?: privateProps.getProperty("glacia.experimentalNativeCalls")
    )?.trim()?.equals("true", ignoreCase = true) == true


android {
    namespace = "dev.glacia.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.glacia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.3.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FCM_CONFIGURED", "${file("google-services.json").isFile}")
        buildConfigField("int", "TELEGRAM_API_ID", "$telegramApiId")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean", "EXPERIMENTAL_NATIVE_CALLS", experimentalNativeCalls.toString()
            )
        }
        release {
            isMinifyEnabled = false
            // Release builds never expose unverified native calling, even if the
            // local debug property is set to true.
            buildConfigField("boolean", "EXPERIMENTAL_NATIVE_CALLS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-glass:2.0.1")
    implementation("io.github.tdlib-android:core:0.1.1")
    // Native libwebrtc/ntgcalls (LGPL-3.0). Calls disabled by default until device QA.
    implementation("io.github.pytgcalls:ntgcalls:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
}
