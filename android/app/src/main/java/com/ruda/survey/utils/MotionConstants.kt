package com.ruda.survey.utils

import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.animation.TimeInterpolator

/**
 * Centralized motion tokens for the RUDA Survey app.
 *
 * "Surveyor's field instrument" — precise, trustworthy, restrained.
 * Every transition answers: "where did my data just go / where am I now."
 */
object MotionConstants {

    // ========================================
    // DURATIONS (ms)
    // ========================================
    /** Micro: state flips, ripples */
    const val DURATION_MICRO = 120L
    /** Standard: in-place content change */
    const val DURATION_STANDARD = 250L
    /** Complex: shared-element / container-transform */
    const val DURATION_COMPLEX = 375L

    // Screen-specific durations
    const val SPLASH_LOGO_ENTER = 400L
    const val SPLASH_STAGGER_DELAY = 80L
    const val SPLASH_HOLD = 2500L
    const val NAV_TRANSITION = 350L
    const val FIELD_SHAKE = 400L
    const val BUTTON_MORPH = 150L
    const val FLOAT_LABEL = 150L
    const val PANEL_EXPAND = 250L
    const val CARD_STAGGER = 40L
    const val CARD_STAGGER_CAP = 400L
    const val COUNT_UP = 700L
    const val PULSE_ONCE = 600L
    const val SHIMMER = 1200L
    const val CHECKMARK_DRAW = 500L
    const val THUMBNAIL_FLY = 350L
    const val EMPTY_FLOAT_AMPLITUDE = 4 // dp
    const val EMPTY_FLOAT_DURATION = 3000L

    // ========================================
    // EASING CURVES
    // ========================================
    /** Entrance: emphasized-decelerate */
    val EASING_ENTRANCE: TimeInterpolator = DecelerateInterpolator(2.2f)

    /** Exit: emphasized-accelerate */
    val EASING_EXIT: TimeInterpolator = AccelerateInterpolator(2.2f)

    /** Simple state change: standard */
    val EASING_STANDARD: TimeInterpolator = DecelerateInterpolator(1.5f)

    /** Linear (for shimmer) */
    val EASING_LINEAR: TimeInterpolator = LinearInterpolator()

    // ========================================
    // REDUCED MOTION FALLBACK
    // ========================================
    /** If reduced motion is enabled, collapse all durations to this */
    const val REDUCED_MOTION_DURATION = 100L

    /**
     * Returns the appropriate duration considering the system's
     * "remove animations" accessibility setting.
     */
    fun duration(normal: Long, isReducedMotion: Boolean): Long {
        return if (isReducedMotion) REDUCED_MOTION_DURATION else normal
    }
}
