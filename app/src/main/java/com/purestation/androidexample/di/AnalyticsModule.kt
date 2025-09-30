package com.purestation.androidexample.di

import com.purestation.androidexample.analytics.AnalyticsService
import com.purestation.androidexample.analytics.AnalyticsServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalytics(impl: AnalyticsServiceImpl): AnalyticsService
}