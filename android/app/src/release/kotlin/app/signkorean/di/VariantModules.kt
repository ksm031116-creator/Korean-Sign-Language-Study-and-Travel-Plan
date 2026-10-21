package app.signkorean.di

import org.koin.core.module.Module

/** 릴리스는 덮어쓰는 조립이 없다. 실제 Play 결제만 쓴다 (설계서 §5.5, AR-04). */
val variantModules: List<Module> = emptyList()
