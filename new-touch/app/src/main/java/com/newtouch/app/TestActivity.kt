package com.newtouch.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {

    private lateinit var trail: TrailView
    private lateinit var btnStart: TextView
    private var startedHere = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        trail = findViewById(R.id.trail)
        btnStart = findViewById(R.id.btnStart)
        val btnClear = findViewById<TextView>(R.id.btnClear)
        val btnBack = findViewById<android.view.View>(R.id.btnBack)
        listOf(btnStart, btnClear, btnBack).forEach { it.pressEffect() }

        btnBack.setOnClickListener { finish() }
        btnClear.setOnClickListener { trail.clear() }
        btnStart.setOnClickListener {
            val svc = TouchService.instance
            if (svc == null) {
                Toast.makeText(this, "Ative o serviço de acessibilidade primeiro.", Toast.LENGTH_SHORT).show()
                go(PermissionsActivity::class.java)
                return@setOnClickListener
            }
            when (svc.state) {
                TouchService.State.RUNNING -> svc.pause()
                else -> { startedHere = true; svc.start() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TouchService.uiListener = { updateButton() }
        updateButton()
    }

    override fun onPause() {
        super.onPause()
        TouchService.uiListener = null
    }

    override fun onStop() {
        super.onStop()
        if (startedHere) {
            TouchService.instance?.stop()
            startedHere = false
        }
    }

    private fun updateButton() {
        btnStart.text = when (TouchService.instance?.state) {
            TouchService.State.RUNNING -> "Pausar"
            TouchService.State.PAUSED -> "Retomar"
            else -> "Iniciar"
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
