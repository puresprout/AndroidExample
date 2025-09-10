package com.purestation.androidexample.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LogEntry(val level: String, val message: String, val ts: Long = System.currentTimeMillis()) : Parcelable