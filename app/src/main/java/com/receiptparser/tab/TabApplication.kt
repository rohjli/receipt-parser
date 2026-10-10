package com.receiptparser.tab

import android.app.Application
import com.receiptparser.tab.data.ReminderWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp class TabApplication : Application() {
    override fun onCreate() { super.onCreate(); ReminderWorker.schedule(this) }
}
