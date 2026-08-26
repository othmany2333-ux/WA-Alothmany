package com.alothmany.wa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.core.ui.WAAlOthmanyRoot
import com.alothmany.wa.system.integration.SystemIntegrationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLogger: AppLogger
    @Inject lateinit var systemIntegration: SystemIntegrationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        systemIntegration.initialize()
        appLogger.info("APP", getString(R.string.app_started_log))
        setContent { WAAlOthmanyRoot() }
    }

    override fun onResume() {
        super.onResume()
        systemIntegration.refresh()
    }
}
