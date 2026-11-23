// Kotlin 2.4 security fixes require R8 >= 9.1.29; keep the existing AGP DSL.
// https://developer.android.com/build/kotlin-support
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.57")
    }
    // AGP's build-only transitives also process untrusted archives, XML and network data.
    // Keep compatible patch releases until AGP itself supplies these security fixes.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val fixedVersion = when {
                requested.group == "io.netty" && !requested.name.startsWith("netty-tcnative") -> "4.1.139.Final"
                requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.86"
                requested.group == "org.apache.commons" && requested.name == "commons-compress" -> "1.28.0"
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> "0.9.7"
                requested.group == "org.jdom" && requested.name == "jdom2" -> "2.0.6.1"
                requested.group == "com.google.guava" && requested.name == "guava" -> "33.7.2-jre"
                else -> null
            }
            if (fixedVersion != null) {
                useVersion(fixedVersion)
                because("Patched build dependency; see docs/security-audit-2026-10-23.md")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Media3 and CameraX pull older Guava; GHSA-xxph-c9ww-hj94 (CVE-2026-102554) is fixed in 33.7.2.
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.guava" && requested.name == "guava") {
                useVersion("33.7.2-android")
                because("GHSA-xxph-c9ww-hj94: Guava deserialization memory exhaustion fixed in 33.7.2")
            }
        }
    }
}

