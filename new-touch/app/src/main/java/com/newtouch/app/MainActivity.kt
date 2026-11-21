package com.newtouch.app

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var pulse: ObjectAnimator? = null

    private lateinit var statusRing: View
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var btnMain: TextView
    private lateinit var pointText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusRing = findViewById(R.id.statusRing)
        statusDot = findViewById(R.id.statusDot)
        statusTitle = findViewById(R.id.statusTitle)
        statusSub = findViewById(R.id.statusSub)
        btnMain = findViewById(R.id.btnMain)
        pointText = findViewById(R.id.pointText)

        val btnPick = findViewById<TextView>(R.id.btnPick)
        val btnCenter = findViewById<TextView>(R.id.btnCenter)
        val btnTest = findViewById<TextView>(R.id.btnTest)
        val btnPerms = findViewById<TextView>(R.id.btnPerms)
        listOf(btnMain, btnPick, btnCenter, btnTest, btnPerms).forEach { it.pressEffect() }

        // velocidade
        val speedSlider = findViewById<Slider>(R.id.speedSlider)
        val speedValue = findViewById<TextView>(R.id.speedValue)
        speedSlider.value = prefs.speed.toFloat()
        speedValue.text = "${prefs.speed}/10"
        speedSlider.addOnChangeListener { _, value, _ ->
            prefs.speed = value.toInt()
            speedValue.text = "${value.toInt()}/10"
        }

        // alcance
        val ampSlider = findViewById<Slider>(R.id.ampSlider)
        val ampValue = findViewById<TextView>(R.id.ampValue)
        ampSlider.value = prefs.amplitude.toFloat().coerceIn(20f, 100f)
        ampValue.text = "${prefs.amplitude}%"
        ampSlider.addOnChangeListener { _, value, _ ->
            prefs.amplitude = value.toInt()
            ampValue.text = "${value.toInt()}%"
        }

        // direção
        val chips = findViewById<ChipGroup>(R.id.chipGroup)
        val chipIds = listOf(R.id.chipH, R.id.chipV, R.id.chipD, R.id.chipM) // Dir.H, V, D, MIXED
        chips.check(chipIds[prefs.direction.coerceIn(0, 3)])
        chips.setOnCheckedStateChangeListener { _, ids ->
            val idx = chipIds.indexOf(ids.firstOrNull())
            if (idx >= 0) prefs.direction = idx
        }

        // botão principal
        btnMain.setOnClickListener {
            val svc = TouchService.instance
            when {
                svc == null -> go(PermissionsActivity::class.java)
                svc.floatingVisible -> { prefs.enabled = false; svc.hideFloating() }
                else -> svc.showFloating()
            }
            refresh()
        }

        // ponto de toque
        btnPick.setOnClickListener {
            val svc = TouchService.instance
            if (svc == null) {
                Toast.makeText(this, "Ative o serviço de acessibilidade primeiro.", Toast.LENGTH_SHORT).show()
                go(PermissionsActivity::class.java)
            } else {
                svc.startPick()
            }
        }
        btnCenter.setOnClickListener {
            prefs.clearPoint()
            refresh()
        }

        btnTest.setOnClickListener { go(TestActivity::class.java) }
        btnPerms.setOnClickListener { go(PermissionsActivity::class.java) }

        animateEntrance()
    }

    private fun animateEntrance() {
        val content = findViewById<LinearLayout>(R.id.content)
        for (i in 0 until content.childCount) {
            val c = content.getChildAt(i)
            c.alpha = 0f
            c.translationY = 36f
            c.animate().alpha(1f).translationY(0f)
                .setStartDelay(i * 60L).setDuration(380).start()
        }
    }

    override fun onResume() {
        super.onResume()
        TouchService.uiListener = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        TouchService.uiListener = null
        setPulse(false)
    }

    private fun setPulse(on: Boolean) {
        if (on) {
            if (pulse == null) pulse = pulseAnimator(statusRing).also { it.start() }
        } else {
            pulse?.cancel()
            pulse = null
            statusRing.alpha = 0f
            statusRing.scaleX = 1f
            statusRing.scaleY = 1f
        }
    }

    private fun refresh() {
        val svc = TouchService.instance
        val connected = svc != null
        val floating = svc?.floatingVisible == true
        val st = svc?.state ?: TouchService.State.IDLE

        val title: String
        val sub: String
        val colorRes: Int
        val btn: String
        var running = false
        when {
            !connected -> {
                title = "Permissão necessária"
                sub = "Ative o serviço de acessibilidade do New Touch."
                colorRes = R.color.gray_dot
                btn = "Conceder permissão"
            }
            !floating -> {
                title = "Desativado"
                sub = "Toque em Ativar para mostrar o botão flutuante."
                colorRes = R.color.gray_dot
                btn = "Ativar"
            }
            st == TouchService.State.RUNNING -> {
                title = "Em execução"
                sub = "Arraste Contínuo ativo. Use o botão flutuante para pausar."
                colorRes = R.color.blue_primary
                btn = "Desativar"
                running = true
            }
            st == TouchService.State.PAUSED -> {
                title = "Em pausa"
                sub = "Toque no botão flutuante e depois em ▶ para retomar."
                colorRes = R.color.blue_mid
                btn = "Desativar"
            }
            else -> {
                title = "Pronto"
                sub = "Botão flutuante visível. Toque nele e escolha ▶ para iniciar."
                colorRes = R.color.blue_mid
                btn = "Desativar"
            }
        }
        statusTitle.text = title
        statusSub.text = sub
        btnMain.text = btn
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        setPulse(running)

        pointText.text = if (prefs.pointX < 0f) "Centro da tela (padrão)"
        else "Personalizado: x ${prefs.pointX.toInt()}, y ${prefs.pointY.toInt()}"
    }
}
