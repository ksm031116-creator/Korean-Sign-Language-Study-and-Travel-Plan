package app.signkorean.di

import app.signkorean.BuildConfig
import org.koin.core.module.Module

/**
 * 릴리스 조립. 이 앱은 Play 스토어에 올리지 않아 실제 결제가 동작하지 않으므로, 기본은 모의 결제다(설계서 결정 20).
 * 표본 데이터·다시 잠그기는 디버그에만 있다. `-PrealBilling=true` 로 만들면 Play 결제(PlayBillingEntitlementGateway)를 쓴다.
 */
val variantModules: List<Module> = if (BuildConfig.SIMULATED_BILLING) listOf(simulatedBillingModule) else emptyList()
