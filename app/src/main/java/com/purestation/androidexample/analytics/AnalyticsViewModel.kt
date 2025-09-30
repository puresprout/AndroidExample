package com.purestation.androidexample.analytics

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val service: AnalyticsService) : ViewModel() {
    fun analytics() {
        println("AnalyticsViewModel - $this")

        println("analytics")

        service.analyticsMethods()
    }
}