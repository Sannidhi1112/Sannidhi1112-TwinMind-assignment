package com.twinmind.voicerecorder.di

import android.content.Context
import dagger.hilt.android.EntryPointAccessors

inline fun <reified T> Context.workerEntryPoint(): T {
    return EntryPointAccessors.fromApplication(
        applicationContext,
        T::class.java
    )
}
