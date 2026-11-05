import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Релизный ключ лежит вне репозитория (F:\android); пароли — в keystore.properties. */
val keystoreProps = Properties().apply {
    val f = file("F:/android/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.family.sleepschedule"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.family.sleepschedule"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.14"
    }

    signingConfigs {
        create("release") {
            storeFile = file("F:/android/basleep.keystore")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = "basleep"
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation("junit:junit:4.13.2")
}
