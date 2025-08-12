package com.purestation.viewbasedexample.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlowViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()

    private var job: Job? = null

    fun startCounting() {
        job = viewModelScope.launch {
            for (i in 1..10) {
                _count.update { it + 1 }

//                Log.d("psh", _count.value.toString())

                delay(1000)
            }
        }
    }

    fun stopCounting() {
        job?.cancel()
    }
}