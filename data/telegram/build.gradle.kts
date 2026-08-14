plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.zalexanninev15.tokitoki.data.telegram"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("main") {
            // Filled by the `build tdlib` workflow:
            //   jniLibs/<abi>/libtdjson.so  — the native library, one per ABI
            //   java/org/drinkless/tdlib/   — TdApi.java and Client.java, generated
            // Both are gitignored: they are build output, not source.
            jniLibs.srcDirs("src/main/jniLibs")
            java.srcDirs("src/main/java")
        }
    }
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
}
