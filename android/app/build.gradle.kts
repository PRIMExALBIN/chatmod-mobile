plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun releaseSecret(gradleName: String, envName: String) =
    providers.gradleProperty(gradleName).orElse(providers.environmentVariable(envName))

val releaseStoreFilePath = releaseSecret("chatmodReleaseStoreFile", "CHATMOD_RELEASE_STORE_FILE").orNull
val releaseStorePassword = releaseSecret("chatmodReleaseStorePassword", "CHATMOD_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = releaseSecret("chatmodReleaseKeyAlias", "CHATMOD_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = releaseSecret("chatmodReleaseKeyPassword", "CHATMOD_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { value -> !value.isNullOrBlank() }

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.activity:activity:1.10.0",
        "androidx.activity:activity-compose:1.10.0",
        "androidx.activity:activity-ktx:1.10.0"
    )
}

android {
    namespace = "com.chatmod.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chatmod.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "CHATMOD_API_BASE_URL",
            "\"${providers.gradleProperty("chatmodApiBaseUrl").orElse("http://10.0.2.2:4100").get()}\""
        )
        buildConfigField(
            "Boolean",
            "CHATMOD_USE_DEMO_API",
            providers.gradleProperty("chatmodUseDemoApi").orElse("false").get()
        )
    }

    flavorDimensions += "channel"
    productFlavors {
        create("internal") {
            dimension = "channel"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            resValue("string", "app_name", "ChatMod Internal")
        }
        create("closedBeta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "ChatMod Beta")
        }
        create("production") {
            dimension = "channel"
            resValue("string", "app_name", "ChatMod Mobile")
        }
    }

    signingConfigs {
        create("externalRelease") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("externalRelease")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("verifyReleaseSigningConfigured") {
    group = "verification"
    description = "Verifies release signing is supplied through env vars or Gradle properties."
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Release signing is not configured. Set CHATMOD_RELEASE_STORE_FILE, " +
                    "CHATMOD_RELEASE_STORE_PASSWORD, CHATMOD_RELEASE_KEY_ALIAS, and " +
                    "CHATMOD_RELEASE_KEY_PASSWORD, or the matching chatmodRelease* Gradle properties."
            )
        }

        val storeFile = file(releaseStoreFilePath!!)
        if (!storeFile.isFile) {
            throw GradleException("Release keystore file does not exist: ${storeFile.absolutePath}")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = enforcedPlatform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")

    implementation(platform("io.insert-koin:koin-bom:4.0.4"))
    implementation("io.insert-koin:koin-android")
}
