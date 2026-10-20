plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.signkorean"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.signkorean"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // values-xx/ 를 추가하면 앱별 언어 목록이 자동으로 따라온다 (손으로 쓴 locales_config 와 어긋날 일이 없다).
    androidResources {
        generateLocaleConfig = true
    }

    // 번역 누락은 경고가 아니라 오류 (설계서 §8.8, AR-06 과 함께 다국어 품질 게이트)
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        error += "MissingTranslation"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // ContentModuleTest 가 File 로 직접 읽는 내장 팩. 입력으로 알려야 팩만 바뀌어도 다시 실행된다.
            it.inputs.dir(rootProject.file("adapter/src/main/assets/content"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapter"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.koin.android)

    testImplementation(project(":testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.konsist)
}
