// 테스트 전용 가짜 구현. 다른 모듈은 testImplementation 으로만 의존한다 (AR-04).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain"))
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
