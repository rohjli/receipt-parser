package com.receiptparser.tab

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.receiptparser.tab.ui.TabApp
import com.receiptparser.tab.ui.TabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)
    private var home by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        setContent { TabTheme { TabApp(incoming, { incoming = null }, home, { home = false }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent) }
    private fun readIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            incoming = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        home = intent.getBooleanExtra("open_home", false)
    }
}
