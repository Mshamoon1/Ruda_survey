package com.ruda.survey.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.ruda.survey.R
import com.ruda.survey.ui.MainActivity
import com.ruda.survey.utils.MotionConstants

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.ivLogo)
        val title = findViewById<View>(R.id.tvAppName)
        val subtitle = findViewById<View>(R.id.tvSubtitle)
        val orgName = findViewById<View>(R.id.tvOrgName)
        val topArc = findViewById<View>(R.id.topArc)
        val progressBar = findViewById<View>(R.id.progressBar)

        // Start arc parallax drift (slow, subtle sweep like flowing water)
        topArc.animate()
            .translationX(20f)
            .setDuration(MotionConstants.SPLASH_HOLD)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .withEndAction {
                topArc.animate()
                    .translationX(0f)
                    .setDuration(MotionConstants.SPLASH_HOLD)
                    .setInterpolator(DecelerateInterpolator(1.2f))
                    .start()
            }
            .start()

        // Logo: scale-in from 0.85→1.02→1.0 + fade, 400ms
        logo.alpha = 0f
        logo.scaleX = 0.85f
        logo.scaleY = 0.85f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).setDuration(400),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.85f, 1.02f, 1f).setDuration(400),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.85f, 1.02f, 1f).setDuration(400)
            )
            interpolator = MotionConstants.EASING_ENTRANCE
            startDelay = 200
            start()
        }

        // Title + subtitle: stagger in 80ms after logo, 60ms apart
        val textViews = listOf(title, subtitle, orgName)
        textViews.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(600 + (index * 60L))
                .setInterpolator(MotionConstants.EASING_ENTRANCE)
                .start()
        }

        // Replace generic spinner with thin branded arc
        progressBar.alpha = 0f
        progressBar.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(800)
            .start()

        // Navigate after 2.5s with cross-fade
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
        }, MotionConstants.SPLASH_HOLD)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
