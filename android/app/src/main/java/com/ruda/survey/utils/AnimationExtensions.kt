package com.ruda.survey.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.provider.Settings
import android.view.View
import com.ruda.survey.R

// ========================================
// SYSTEM HELPERS
// ========================================

/**
 * Check if the system "remove animations" setting is enabled.
 */
fun View.isReducedMotionEnabled(): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

// ========================================
// TAP FEEDBACK
// ========================================

/**
 * Apply a subtle scale tap feedback (1.0 → 0.92 → 1.0).
 * Use on clickable cards/buttons for tactile response.
 */
fun View.animateTapFeedback(
    onAnimEnd: (() -> Unit)? = null
) {
    if (isReducedMotionEnabled()) {
        onAnimEnd?.invoke()
        return
    }
    val scaleDown = 0.92f
    AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@animateTapFeedback, "scaleX", 1f, scaleDown),
            ObjectAnimator.ofFloat(this@animateTapFeedback, "scaleY", 1f, scaleDown)
        )
        duration = MotionConstants.DURATION_MICRO
        interpolator = MotionConstants.EASING_STANDARD
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(this@animateTapFeedback, "scaleX", scaleDown, 1f),
                        ObjectAnimator.ofFloat(this@animateTapFeedback, "scaleY", scaleDown, 1f)
                    )
                    duration = MotionConstants.DURATION_MICRO
                    interpolator = MotionConstants.EASING_STANDARD
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            onAnimEnd?.invoke()
                        }
                    })
                    start()
                }
            }
        })
        start()
    }
}

// ========================================
// SHAKE
// ========================================

/**
 * Horizontal shake for invalid fields. ~6dp amplitude, 3 cycles, 400ms.
 */
fun View.shake() {
    if (isReducedMotionEnabled()) return
    ObjectAnimator.ofFloat(this, "translationX",
        0f, -18f, 18f, -12f, 12f, -6f, 6f, 0f
    ).apply {
        duration = MotionConstants.FIELD_SHAKE
        start()
    }
}

// ========================================
// PULSE
// ========================================

/**
 * Single, non-looping pulse (alpha 1→0.5→1, 600ms).
 * Draws attention once without nagging.
 */
fun View.pulseOnce() {
    if (isReducedMotionEnabled()) return
    AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@pulseOnce, "alpha", 1f, 0.5f, 1f)
        )
        duration = MotionConstants.PULSE_ONCE
        start()
    }
}

// ========================================
// STAGGERED LIST ENTRANCE
// ========================================

/**
 * Animate a list of views in with staggered fade-up.
 * Each view enters with a delay based on its index.
 */
fun List<View>.staggerFadeIn(
    staggerDelay: Long = MotionConstants.CARD_STAGGER,
    maxTotalDelay: Long = MotionConstants.CARD_STAGGER_CAP,
    perItemDuration: Long = MotionConstants.DURATION_STANDARD
) {
    if (isNullOrEmpty()) return
    val reducedMotion = first().isReducedMotionEnabled()
    forEachIndexed { index, view ->
        val delay = if (reducedMotion) 0L else minOf(index * staggerDelay, maxTotalDelay)
        view.alpha = 0f
        view.translationY = 16f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(if (reducedMotion) MotionConstants.REDUCED_MOTION_DURATION else perItemDuration)
            .setStartDelay(delay)
            .setInterpolator(MotionConstants.EASING_ENTRANCE)
            .start()
    }
}

// ========================================
// PANEL EXPAND / COLLAPSE
// ========================================

/**
 * Expand a view with height+fade animation.
 */
fun View.expandWithFade(duration: Long = MotionConstants.PANEL_EXPAND) {
    if (isReducedMotionEnabled()) {
        visibility = View.VISIBLE
        return
    }
    alpha = 0f
    visibility = View.VISIBLE
    val parent = parent as? View

    measure(
        android.view.View.MeasureSpec.makeMeasureSpec(
            (parent?.width ?: width) - (parent?.paddingStart ?: 0) - (parent?.paddingEnd ?: 0),
            android.view.View.MeasureSpec.AT_MOST
        ),
        android.view.View.MeasureSpec.UNSPECIFIED
    )
    val targetMeasuredHeight = measuredHeight

    animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(MotionConstants.EASING_ENTRANCE)
        .start()

    // Animate height
    layoutParams = layoutParams.also { it.height = 1 }
    post {
        animate().withEndAction {
            layoutParams = layoutParams.also { it.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }
        }.setDuration(duration).setInterpolator(MotionConstants.EASING_ENTRANCE).start()
    }
}

/**
 * Collapse a view with fade animation.
 */
fun View.collapseWithFade(duration: Long = MotionConstants.PANEL_EXPAND) {
    if (isReducedMotionEnabled()) {
        visibility = View.GONE
        return
    }
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(MotionConstants.EASING_EXIT)
        .withEndAction {
            visibility = View.GONE
            alpha = 1f
        }
        .start()
}

// ========================================
// FLOATING IDLE (empty state)
// ========================================

/**
 * Start a gentle floating idle animation (±4dp, ~3s loop).
 * Returns the animator so it can be cancelled.
 */
fun View.startFloatingIdle(
    amplitude: Int = MotionConstants.EMPTY_FLOAT_AMPLITUDE,
    duration: Long = MotionConstants.EMPTY_FLOAT_DURATION
): ValueAnimator? {
    if (isReducedMotionEnabled()) return null
    return ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = MotionConstants.EASING_LINEAR
        addUpdateListener { animator ->
            val fraction = animator.animatedValue as Float
            translationY = amplitude * (2f * fraction - 1f)
        }
        start()
    }
}

// ========================================
// COUNT-UP
// ========================================

/**
 * Animate a number from 0 to [targetValue] over [duration]ms.
 * Updates the provided callback with each frame value.
 */
fun animateCountUp(
    targetValue: Int,
    duration: Long = MotionConstants.COUNT_UP,
    onUpdate: (Int) -> Unit
): ValueAnimator? {
    return ValueAnimator.ofInt(0, targetValue).apply {
        this.duration = duration
        interpolator = MotionConstants.EASING_STANDARD
        addUpdateListener { animator ->
            onUpdate(animator.animatedValue as Int)
        }
        start()
    }
}
