plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 스크린샷 테스트. 기준 이미지는 app/src/test/screenshots 에 둔다 (record: ./gradlew recordRoborazziDebug)
    alias(libs.plugins.roborazzi)
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
        // 중계 서버(PRD §10.3) 주소. 서버가 준비되면 -PapiBaseUrl=https://... 로 넣는다. 비어 있으면 리뷰·평점은
        // "연결되지 않음"이다(릴리스) 또는 표본(디버그). 공급자 비밀키는 서버에 있고 앱에는 없다(설계서 원칙 13).
        val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?).orEmpty().trim()
        // https 만 받는다(NFR05). 따옴표·역슬래시가 들어가면 BuildConfig 가 깨지므로 그것도 막는다(Phase 15 리뷰 RVW-05).
        require(apiBaseUrl.isEmpty() || Regex("""https://[^"\\\s]+""").matches(apiBaseUrl)) {
            "apiBaseUrl 은 https:// 로 시작하는 주소여야 한다: $apiBaseUrl"
        }
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // 결제 방식. Play 스토어에 올리지 않으므로 기본은 모의 결제다(디버그·릴리스 모두). 실제 Play 결제: -PrealBilling=true
        buildConfigField("boolean", "SIMULATED_BILLING", (project.findProperty("realBilling") != "true").toString())
    }

    buildTypes {
        debug {
            // 의사 언어 en-XA(긴 문구)·ar-XB(오른쪽→왼쪽)로 레이아웃을 검사한다 (설계서 §8.7, L10N-09).
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = false
            // 스토어에 올리지 않고 APK 로 나눠 주므로 디버그 키로 서명해 바로 설치할 수 있게 한다.
            signingConfig = signingConfigs.getByName("debug")
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // ContentModuleTest 가 File 로 직접 읽는 내장 팩. 입력으로 알려야 팩만 바뀌어도 다시 실행된다.
            it.inputs.dir(rootProject.file("adapter/src/main/assets/content"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// 스토어에 올리는 번들(bundleRelease)은 중계 서버 주소 없이 만들지 않는다(Phase 15 리뷰 RVW-07). 서버가 없으면 리뷰·평점·경로가
// 모두 "연결되지 않음"인 앱이 출시된다. assembleRelease(CI 게이트의 릴리스 조립 검사)는 서버 없이도 만든다.
val storeApiBaseUrl = (project.findProperty("apiBaseUrl") as String?).orEmpty().trim()
val checkStoreRelease by tasks.registering {
    group = "verification"
    description = "스토어 번들에 중계 서버 주소가 들어가는지 확인한다 (RVW-07)."
    val url = storeApiBaseUrl
    val realBilling = project.findProperty("realBilling") == "true"
    doLast {
        check(url.isNotEmpty()) { "-PapiBaseUrl=https://... is required for store bundles (RVW-07). 스토어 번들에는 -PapiBaseUrl=https://... 이 필요하다. 서버 없이 출시하면 리뷰·경로가 모두 비어 있다 (RVW-07)." }
        check(realBilling) { "Store bundles need real billing: -PrealBilling=true. 스토어 번들은 모의 결제로 만들지 않는다." }
    }
}
// bundleRelease 와 그 하위 작업(packageReleaseBundle·signReleaseBundle)을 직접 불러도 걸리게 한다.
tasks.matching { it.name == "bundleRelease" || it.name.endsWith("ReleaseBundle") }.configureEach { dependsOn(checkStoreRelease) }

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapter"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(project(":testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.konsist)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
