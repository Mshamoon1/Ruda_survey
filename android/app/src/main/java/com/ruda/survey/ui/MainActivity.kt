package com.ruda.survey.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.ruda.survey.R
import com.ruda.survey.databinding.ActivityMainBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.ruda.survey.data.remote.RepositoryFactory
import com.ruda.survey.BuildConfig

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var sessionStorageReady: Boolean? = null
    private var contentInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            sessionStorageReady = withContext(Dispatchers.IO) {
                runCatching { RepositoryFactory.getTokenManager(applicationContext) }.isSuccess
            }
            initializeContentWhenResumed()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        initializeContentWhenResumed()
    }

    private fun initializeContentWhenResumed() {
        // Storage initialization can finish after Home, rotation or onSaveInstanceState.
        // Wait for a resumed activity before inflating NavHostFragment or navigating.
        if (contentInitialized || sessionStorageReady == null || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            supportFragmentManager.isStateSaved) return
        contentInitialized = true
        if (sessionStorageReady != true) {
            setContentView(android.widget.TextView(this@MainActivity).apply {
                text = "Secure session storage is unavailable. Restart the app to retry. Local surveys have not been deleted."
                setPadding(32, 48, 32, 32)
            })
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val nav = navHostFragment.navController
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    val allowed = withContext(Dispatchers.IO) {
                        BuildConfig.DEMO_MODE || runCatching {
                            RepositoryFactory.getTokenManager(applicationContext).isOfflineAccessAllowed()
                        }.getOrDefault(false)
                    }
                    if (!allowed && !supportFragmentManager.isStateSaved &&
                        nav.currentDestination?.id != R.id.loginFragment) {
                        nav.navigate(R.id.loginFragment, null, NavOptions.Builder()
                            .setPopUpTo(nav.graph.id, true).build())
                    }
                    delay(30_000)
                }
            }
        }
    }
}
