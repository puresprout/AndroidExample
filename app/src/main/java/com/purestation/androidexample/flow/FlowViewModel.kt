package com.purestation.androidexample.flow

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FlowViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()

    fun startCounting() {
        viewModelScope.launch {
            for (i in 1..10) {
                _count.value += 1

                Log.i("psh", _count.value.toString())

                delay(1000)
            }
        }
    }
}