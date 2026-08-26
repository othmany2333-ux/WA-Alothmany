package com.alothmany.wa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.alothmany.wa.core.logging.AppLogger
import com.alothmany.wa.core.ui.WAAlOthmanyRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLogger: AppLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        appLogger.info("APP", getString(R.string.app_started_log))
        setContent { WAAlOthmanyRoot() }
    }
}
