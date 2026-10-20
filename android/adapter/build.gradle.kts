// 포트 구현(어댑터). :app 을 모른다 (설계서 §4.2).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Room 스키마를 저장소에 남긴다. 마이그레이션 테스트가 이 파일을 쓴다 (설계서 §7.2).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // MigrationTestHelper 가 내보낸 스키마를 assets 처럼 읽는다.
        sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
        unitTests.all {
            // 연결 감사 스냅샷(C-07) 갱신: ./gradlew :adapter:testDebugUnitTest -PupdateSnapshots=true
            it.systemProperty("updateSnapshots", project.findProperty("updateSnapshots") ?: "false")
            // 테스트가 File 로 직접 읽는 데이터. 입력으로 알려야 데이터만 바꿨을 때 테스트가 UP-TO-DATE 로 건너뛰지 않는다.
            it.inputs.dir("src/main/assets/content")
            it.inputs.file(rootProject.file("content-id-lock.json"))
        }
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
    // 콘텐츠 팩 JSON. 컴파일러 플러그인 없이 JsonElement 로 읽어 필드별 오류 위치를 직접 알린다.
    implementation(libs.kotlinx.serialization.json)
    // 앱별 언어(per-app language). 언어 값의 저장은 AppCompat 하나만 한다 (설계서 §8.2).
    implementation(libs.androidx.appcompat)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Robolectric 이 JUnit 4 러너만 지원하므로 Android 모듈의 단위 테스트는 JUnit 4 로 쓴다 (설계서 §9.1).
    testImplementation(project(":testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
}
