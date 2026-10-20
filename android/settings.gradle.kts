pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SignKorean"

// 경량 헥사고날 (IMPLEMENTATION_DESIGN.md §4.2)
//   :domain  순수 Kotlin — 규칙·포트·유스케이스. Android·Koin·SDK를 모른다
//   :testing 순수 Kotlin — 가짜 구현·고정 시계. 프로덕션 의존성에 들어가지 않는다
//   :adapter Android 라이브러리 — 포트 구현
//   :app     Android 앱 — UI와 Koin 조립(Composition Root)
include(":domain", ":testing", ":adapter", ":app")
