import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Fixed: Look for keystore.properties in the current module (app) instead of rootProject
val keystorePropertiesFile = project.file("keystore.properties")
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
        versionCode = 78
        versionName = "1.1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    flavorDimensions += "appType"
    productFlavors {
        create("keyboard") {
            dimension = "appType"
            applicationId = "com.iqbal.gurmukhikeyboard50"
            versionNameSuffix = "-full"
            manifestPlaceholders["appLabel"] = "Gurmukhi Punjabi keyboard"
            manifestPlaceholders["launcherActivity"] = ".MainActivity"
        }
        create("nitnem") {
            dimension = "appType"
            applicationId = "com.iqbal.nitnem.punjabi"
            versionCode = 78
            versionName = "1.1.7"
            versionNameSuffix = "-nitnem"
            manifestPlaceholders["appLabel"] = "Nitnem Gutka"
            manifestPlaceholders["launcherActivity"] = ".NitnemActivity"
        }
        create("calendar") {
            dimension = "appType"
            applicationId = "com.iqbal.nanakshahi.calendar"
            versionCode = 78
            versionName = "1.1.7"
            versionNameSuffix = "-calendar"
            manifestPlaceholders["appLabel"] = "Nanakshahi Jantri"
            manifestPlaceholders["launcherActivity"] = ".CalendarActivity"
        }
        create("calculator") {
            dimension = "appType"
            applicationId = "com.iqbal.punjabi.calculator"
            versionCode = 78
            versionName = "1.1.7"
            versionNameSuffix = "-calculator"
            manifestPlaceholders["appLabel"] = "Punjabi Calculator"
            manifestPlaceholders["launcherActivity"] = ".CalculatorActivity"
        }
    }

    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/._*"
            excludes += "**/DS_Store"
            excludes += "**/._**"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:._*"
    }

    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
            aidl {
                srcDirs("src/main/aidl")
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.android.volley:volley:1.2.1")
    
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    implementation("androidx.emoji2:emoji2:1.5.0")
    implementation("androidx.emoji2:emoji2-views:1.5.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
