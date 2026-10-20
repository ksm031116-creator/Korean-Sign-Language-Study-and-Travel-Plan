// 순수 Kotlin 모듈. Android·Koin·Room·Billing 의존성을 추가하지 않는다 (설계서 원칙 1, AR-01).
plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports { xml.required.set(true) }
}

// 학습 규칙은 분기 커버리지 85% 이상 (구현 계획 Phase 5 완료 조건). 판정 저장 규칙이 여기 모여 있다.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("app.signkorean.domain.learning")
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
