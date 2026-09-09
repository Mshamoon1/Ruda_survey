package com.ruda.survey.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ruda.survey.R
import com.ruda.survey.ui.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.ivLogo)
        val title = findViewById<View>(R.id.tvAppName)
        val subtitle = findViewById<View>(R.id.tvSubtitle)
        val tagline = findViewById<View>(R.id.tvTagline)
        val poweredBy = findViewById<TextView>(R.id.tvPoweredBy)
        val nespakLink = findViewById<TextView>(R.id.tvNespakLink)

        // Fade in logo
        logo.alpha = 0f
        logo.animate().alpha(1f).setDuration(600).setStartDelay(200).start()

        // Stagger text fade-in
        listOf(title, subtitle, tagline).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 12f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(500 + (index * 150L))
                .start()
        }

        // Bottom section fade-in
        listOf(poweredBy, nespakLink).forEach { view ->
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(500).setStartDelay(1000).start()
        }

        // Nespak link click
        nespakLink.paintFlags = nespakLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        nespakLink.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nespak.com.pk/")))
            } catch (_: Exception) { }
        }

        // Navigate after delay
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2500L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
