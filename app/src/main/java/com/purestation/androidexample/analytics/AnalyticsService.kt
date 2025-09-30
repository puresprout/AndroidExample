package com.purestation.androidexample.analytics

interface AnalyticsService {
    fun analyticsMethods()
}

class AnalyticsServiceImpl : AnalyticsService {
    override fun analyticsMethods() {

    }
}

//class AnalyticsServiceImpl @Inject constructor(
//    @ApplicationContext private val ctx: android.content.Context
//) : AnalyticsService {
//    override fun analyticsMethods() {
//
//    }
//}
