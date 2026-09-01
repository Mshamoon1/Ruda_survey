package com.ruda.survey.demo

import com.ruda.survey.BuildConfig

object DemoModeConfig {
    val isDemoMode: Boolean = BuildConfig.DEMO_MODE

    const val DEMO_USERNAME = "DEMO001"
    const val DEMO_PASSWORD = "demo123"
    const val DEMO_USERNAME_2 = "DEMO002"
    const val DEMO_PASSWORD_2 = "demo123"
    const val DEMO_DISPLAY_NAME = "Demo Surveyor"
    const val DEMO_ROLE = "SURVEYOR"
    const val DEMO_DISPLAY_NAME_2 = "Demo Supervisor"
    const val DEMO_ROLE_2 = "SUPERVISOR"
}
