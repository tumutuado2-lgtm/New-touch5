package com.newtouch.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var badge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        badge = findViewById(R.id.badge)

        val btnBack = findViewById<View>(R.id.btnBack)
        val btnAcc = findViewById<View>(R.id.btnOpenAcc)
        val btnInfo = findViewById<View>(R.id.btnOpenInfo)
        listOf(btnBack, btnAcc, btnInfo).forEach { it.pressEffect() }

        btnBack.setOnClickListener { finish() }
        btnAcc.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Abra Configurações > Acessibilidade manualmente.", Toast.LENGTH_LONG).show()
            }
        }
        btnInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        val content = findViewById<LinearLayout>(R.id.content)
        for (i in 0 until content.childCount) {
            val c = content.getChildAt(i)
            c.alpha = 0f
            c.translationY = 30f
            c.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(350).start()
        }
    }

    override fun onResume() {
        super.onResume()
        val ok = TouchService.isEnabledInSettings(this)
        badge.text = if (ok) "Ativo" else "Inativo"
        val color = if (ok) R.color.blue_primary else R.color.gray_dot
        badge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
