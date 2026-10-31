plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version number comes from the GitHub build number (-Pvc=123)
val vc = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.taiful.tfcontacts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taiful.tfcontacts"
        minSdk = 26
        targetSdk = 35
        versionCode = vc
        versionName = "1.0.$vc"
    }

    signingConfigs {
        create("tf") {
            // The signing key file and password are supplied only by the GitHub build.
            val ks = System.getenv("TF_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("TF_KEY_PASSWORD")
                keyAlias = "tfcontacts"
                keyPassword = System.getenv("TF_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("TF_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("tf")
            }
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
