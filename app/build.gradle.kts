import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localSigningProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

val releaseSigningPropertyNames = listOf(
    "CLASS_VIEWER_KEYSTORE_FILE",
    "CLASS_VIEWER_KEYSTORE_PASSWORD",
    "CLASS_VIEWER_KEY_ALIAS",
    "CLASS_VIEWER_KEY_PASSWORD",
)

fun optionalSigningValue(name: String): String? {
    return providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: localSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

val releaseSigningValues = releaseSigningPropertyNames.associateWith(::optionalSigningValue)
val missingReleaseSigningValues = releaseSigningPropertyNames.filter {
    releaseSigningValues[it].isNullOrBlank()
}
val hasCompleteReleaseSigning = missingReleaseSigningValues.isEmpty()

fun releaseSigningValue(name: String): String {
    return releaseSigningValues[name]
        ?: throw GradleException(
            "缺少 Release 签名配置 `$name`。请使用环境变量或根目录 keystore.properties 配置签名（见 README_dev.md）。",
        )
}

fun validateReleaseSigning() {
    if (missingReleaseSigningValues.isNotEmpty()) {
        throw GradleException(
            "Release 打包需要完整签名配置，当前缺少：${missingReleaseSigningValues.joinToString()}。请使用环境变量或根目录 keystore.properties 配置签名（见 README_dev.md）。",
        )
    }

    val keystoreFile = rootProject.file(releaseSigningValue("CLASS_VIEWER_KEYSTORE_FILE"))
    if (!keystoreFile.isFile) {
        throw GradleException("Release 签名文件不存在：${keystoreFile.absolutePath}。")
    }
}

fun isReleasePackagingTaskName(name: String): Boolean {
    return name == "assembleRelease" ||
        name == "bundleRelease" ||
        name == "packageRelease" ||
        name == "installRelease" ||
        name.startsWith("signRelease")
}

val appVersionCode = providers.gradleProperty("app.versionCode")
    .orNull
    ?.toIntOrNull()
    ?: throw GradleException("缺少或无效的 app.versionCode，请在根目录 gradle.properties 配置。")
val appVersionName = providers.gradleProperty("app.versionName")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("缺少或无效的 app.versionName，请在根目录 gradle.properties 配置。")

android {
    namespace = "com.x500x.cursimple"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.x500x.cursimple"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("classViewer") {
                storeFile = rootProject.file(releaseSigningValue("CLASS_VIEWER_KEYSTORE_FILE"))
                storePassword = releaseSigningValue("CLASS_VIEWER_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValue("CLASS_VIEWER_KEY_ALIAS")
                keyPassword = releaseSigningValue("CLASS_VIEWER_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".ci"
            versionNameSuffix = "-ci"
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("classViewer")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        compose = true
        buildConfig = true
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
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

gradle.taskGraph.whenReady {
    val releasePackagingTasks = allTasks.filter {
        it.project == project && isReleasePackagingTaskName(it.name)
    }
    if (releasePackagingTasks.isNotEmpty()) {
        validateReleaseSigning()
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-kernel"))
    implementation(project(":core-data"))
    implementation(project(":core-plugin"))
    implementation(project(":core-reminder"))
    implementation(project(":feature-schedule"))
    implementation(project(":feature-plugin"))
    implementation(project(":feature-widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.sardine.next) {
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "stax", module = "stax")
        exclude(group = "stax", module = "stax-api")
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
