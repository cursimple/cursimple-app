import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localSigningProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun requireSigningValue(name: String): String {
    return providers.environmentVariable(name).orNull
        ?: localSigningProperties.getProperty(name)
        ?: throw GradleException(
            "缺少签名配置 `$name`。请使用环境变量或根目录 keystore.properties 配置签名（见 README）。",
        )
}

val classViewerKeystoreFile = requireSigningValue("CLASS_VIEWER_KEYSTORE_FILE")
val classViewerKeystorePassword = requireSigningValue("CLASS_VIEWER_KEYSTORE_PASSWORD")
val classViewerKeyAlias = requireSigningValue("CLASS_VIEWER_KEY_ALIAS")
val classViewerKeyPassword = requireSigningValue("CLASS_VIEWER_KEY_PASSWORD")

android {
    namespace = "com.kebiao.viewer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kebiao.viewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("classViewer") {
            storeFile = rootProject.file(classViewerKeystoreFile)
            storePassword = classViewerKeystorePassword
            keyAlias = classViewerKeyAlias
            keyPassword = classViewerKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("classViewer")
        }
        release {
            signingConfig = signingConfigs.getByName("classViewer")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":core-kernel"))
    implementation(project(":core-js"))
    implementation(project(":core-data"))
    implementation(project(":feature-schedule"))
    implementation(project(":feature-widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
