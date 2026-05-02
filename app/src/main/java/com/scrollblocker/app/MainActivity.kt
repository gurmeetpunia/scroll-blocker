package com.scrollblocker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateServiceStatus()
            handler.postDelayed(this, 1000) // Refresh every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    // ─────────────────────────────────────────────────────────────
    //  UI built programmatically (no need for XML inflation issues)
    // ─────────────────────────────────────────────────────────────
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var accessibilityButton: MaterialButton
    private lateinit var blockerSwitch: SwitchMaterial

    private fun buildUI(): View {
        val prefs = getSharedPreferences(BlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#0F0F0F"))
        }

        // ── App Title ──
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(28) }
        }

        val appIcon = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { it.marginEnd = dp(14) }
            background = getDrawable(android.R.drawable.ic_menu_close_clear_cancel)
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        TextView(this).apply {
            text = "ScrollBlocker"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            titleCol.addView(this)
        }
        TextView(this).apply {
            text = "Doomscroll defence system"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            titleCol.addView(this)
        }
        titleRow.addView(titleCol)
        root.addView(titleRow)

        // ── Status Card ──
        val statusCard = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
            radius = dp(16).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            cardElevation = 0f
        }

        val statusInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).also { it.marginEnd = dp(14) }
            background = createCircle("#888888")
        }

        val statusText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = TextView(this).apply {
            text = "Service Inactive"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        statusSubtitle = TextView(this).apply {
            text = "Enable accessibility permission below"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        }
        statusText.addView(statusTitle)
        statusText.addView(statusSubtitle)

        statusInner.addView(statusDot)
        statusInner.addView(statusText)
        statusCard.addView(statusInner)
        root.addView(statusCard)

        // ── Enable Button ──
        accessibilityButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Open Accessibility Settings"
            textSize = 15f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(24) }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(accessibilityButton)

        // ── Blocker Toggle ──
        val toggleCard = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(24) }
            radius = dp(16).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            cardElevation = 0f
        }
        val toggleInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        val toggleText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        TextView(this).apply {
            text = "Blocker Active"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            toggleText.addView(this)
        }
        TextView(this).apply {
            text = "Toggle without disabling the service"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            toggleText.addView(this)
        }
        blockerSwitch = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            isChecked = prefs.getBoolean(BlockerAccessibilityService.KEY_ENABLED, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(BlockerAccessibilityService.KEY_ENABLED, isChecked).apply()
            }
        }
        toggleInner.addView(toggleText)
        toggleInner.addView(blockerSwitch)
        toggleCard.addView(toggleInner)
        root.addView(toggleCard)

        // ── Monitored Apps ──
        TextView(this).apply {
            text = "MONITORED APPS"
            textSize = 11f
            setTextColor(Color.parseColor("#666666"))
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
            root.addView(this)
        }

        val apps = listOf(
            Triple("YouTube", "Shorts tab", "#FF0000"),
            Triple("Instagram", "Reels tab", "#C13584"),
            Triple("TikTok", "Entire app", "#010101"),
            Triple("Browsers", "Shorts/Reels URLs", "#4285F4")
        )

        val appsCard = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(16).toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            cardElevation = 0f
        }
        val appsInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        apps.forEachIndexed { index, (name, desc, color) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).also {
                    it.marginEnd = dp(16)
                }
                background = createCircle(color)
                row.addView(this)
            }
            val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textCol.addView(this)
            }
            TextView(this).apply {
                text = desc
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
                textCol.addView(this)
            }
            row.addView(textCol)
            appsInner.addView(row)

            // Divider (not after last item)
            if (index < apps.size - 1) {
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    )
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                    appsInner.addView(this)
                }
            }
        }

        appsCard.addView(appsInner)
        root.addView(appsCard)

        return root
    }

    private fun updateServiceStatus() {
        val active = isAccessibilityServiceEnabled()
        if (active) {
            statusDot.background = createCircle("#00C851")
            statusTitle.text = "Service Active"
            statusSubtitle.text = "Monitoring for Shorts & Reels"
            accessibilityButton.text = "Manage Accessibility Settings"
        } else {
            statusDot.background = createCircle("#FF4444")
            statusTitle.text = "Service Inactive"
            statusSubtitle.text = "Tap below to enable accessibility permission"
            accessibilityButton.text = "Open Accessibility Settings"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/${BlockerAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(serviceName, ignoreCase = true) }
    }

    private fun createCircle(hexColor: String): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
