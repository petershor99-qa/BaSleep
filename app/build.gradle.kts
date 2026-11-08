import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Релизный ключ НЕ хранится в репозитории (репо публичный). Сборка ищет
 * basleep.keystore + keystore.properties по местам по порядку:
 *   1. keystore/ в корне проекта (в .gitignore);
 *   2. G:/Мой диск/apps-keys/basleep/ — личный Google Диск владельца,
 *      синхронизируется на все его компьютеры автоматически.
 * Если ключ не найден, release подписывается debug-ключом с предупреждением —
 * поверх релиза такой APK не встанет.
 */
val keystoreDir = listOf(
    rootProject.file("keystore"),
    File("G:/Мой диск/apps-keys/basleep"),
).firstOrNull { File(it, "basleep.keystore").exists() && File(it, "keystore.properties").exists() }
val keystoreProps = Properties().apply {
    val f = keystoreDir?.let { File(it, "keystore.properties") }
    if (f != null) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreDir != null && keystoreProps.getProperty("storePassword") != null

android {
    namespace = "com.family.sleepschedule"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.family.sleepschedule"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "1.17"
    }

    if (hasReleaseKey) {
        signingConfigs {
            create("release") {
                storeFile = File(keystoreDir, "basleep.keystore")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = "basleep"
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("keystore/basleep.keystore не найден — release подписан debug-ключом. " +
                    "Положите basleep.keystore и keystore.properties в папку keystore/ проекта.")
                signingConfig = signingConfigs.getByName("debug")
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
