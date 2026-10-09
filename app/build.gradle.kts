plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.introcarbon.beatrice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.introcarbon.beatrice"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        externalNativeBuild {
            cmake {
                cppFlags.addAll(listOf("-std=c++20", "-O3", "-march=armv8.2-a+fp16", "-ffast-math"))
                arguments.addAll(listOf("-DANDROID_STL=c++_shared"))
            }
        }
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
    buildFeatures {
        compose = true
        prefab = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // Google Oboe Audio Engine for Low-Latency C++ Audio
    implementation("com.google.oboe:oboe:1.9.0")
    
    // HTTP download for Beatrice models from Hugging Face
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
