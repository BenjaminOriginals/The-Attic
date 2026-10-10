package com.focuslockout

import android.app.Application
import android.content.Context
import com.focuslockout.data.DataStoreRepository

class FocusLockApp : Application() {
    val repository: DataStoreRepository by lazy { DataStoreRepository(this) }
}

/** Process-wide repository: activities and services all share the same DataStore instance. */
val Context.focusRepository: DataStoreRepository
    get() = (applicationContext as FocusLockApp).repository
