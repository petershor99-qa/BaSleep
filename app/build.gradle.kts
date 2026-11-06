import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Релизный ключ лежит в папке keystore/ в корне проекта (НЕ в git — см. .gitignore).
 * Чтобы собирать релиз на новом компьютере: положить туда два файла —
 * basleep.keystore и keystore.properties (перенести со старого компа).
 * Если файлов нет, release-сборка подписывается debug-ключом (для проверки),
 * а для установки поверх релиза всё равно нужен настоящий ключ.
 */
val keystoreDir = rootProject.file("keystore")
val keystoreProps = Properties().apply {
    val f = File(keystoreDir, "keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = File(keystoreDir, "basleep.keystore").exists() &&
    keystoreProps.getProperty("storePassword") != null

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
