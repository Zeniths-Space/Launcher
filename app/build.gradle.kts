import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: the path to a keystore.properties file (storeFile, storePassword, keyAlias,
// keyPassword), given as the Gradle property zenithLauncherSigning, e.g. in ~/.gradle/gradle.properties.
// Keys never live in this repository. Without it, release builds are left unsigned.
val signingProps = providers.gradleProperty("zenithLauncherSigning").orNull
    ?.let { file(it) }
    ?.takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "com.elara.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.elara.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Dev builds install beside the release (com.elara.launcher) instead of clashing with its signature.
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
