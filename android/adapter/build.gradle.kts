// 포트 구현(어댑터). :app 을 모른다 (설계서 §4.2).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.signkorean.adapter"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        error += "MissingTranslation"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    // Robolectric 이 JUnit 4 러너만 지원하므로 Android 모듈의 단위 테스트는 JUnit 4 로 쓴다 (설계서 §9.1).
    testImplementation(project(":testing"))
    testImplementation(libs.junit4)
}
