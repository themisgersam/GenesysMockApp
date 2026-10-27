plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ist.genesysmock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ist.genesysmock"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Default Genesys values; both can be overridden at runtime from the login screen's Settings section.
        buildConfigField("String", "DEFAULT_DEPLOYMENT_ID", "\"your_deployment_id_here\"")
        buildConfigField("String", "DEFAULT_DOMAIN", "\"mypurecloud.de\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation("cloud.genesys:messenger-transport-mobile-sdk-android:2.16.0")

    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
}
