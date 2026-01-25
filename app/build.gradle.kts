
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Read keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.iqbal.gurmukhikeyboard50"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.iqbal.gurmukhikeyboard50"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.9" 
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.annotation:annotation-experimental:1.5.1")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("com.ibm.icu:icu4j:75.1")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.android.volley:volley:1.2.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.google.mlkit:translate:17.0.2")
    implementation(libs.androidx.datastore.core)
    implementation(libs.protolite.well.known.types)
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Emoji2 dependencies
    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.emoji2:emoji2-views:1.5.0")

    implementation("androidx.lifecycle:lifecycle-service:2.8.0")

    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("com.google.mlkit:language-id:17.0.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
