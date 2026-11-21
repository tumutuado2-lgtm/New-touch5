package com.newtouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Serviço de acessibilidade do New Touch.
 *
 * Arraste Contínuo: usa dispatchGesture() com StrokeDescription(willContinue = true) e
 * continueStroke(). Cada trecho do movimento continua o MESMO toque, então o dedo virtual
 * nunca é levantado quando chega ao limite: ele apenas muda de direção.
 */
class TouchService : AccessibilityService() {

    enum class State { IDLE, RUNNING, PAUSED }

    companion object {
        @Volatile
        var instance: TouchService? = null

        /** Chamado (na thread principal) sempre que o estado muda. */
        var uiListener: (() -> Unit)? = null

        fun isEnabledInSettings(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        }
    }

    var state = State.IDLE
        private set
    var floatingVisible = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager

    // ---- motor do gesto ----
    private var stroke: GestureDescription.StrokeDescription? = null
    private var curX = 0f
    private var curY = 0f
    private var cx = 0f
    private var cy = 0f
    private var sign = 1f
    private var dirIndex = 0
    private var segCount = 0
    private var stopRequested = false
    private var finalizing = false
    private var cancelCount = 0
    private var startRetries = 0

    // ---- overlays ----
    private var floatView: View? = null
    private var floatParams: WindowManager.LayoutParams? = null
    private var ringAnim: ObjectAnimator? = null
    private var menuOpen = false
    private var pickView: View? = null

    // ------------------------------------------------------------------ ciclo de vida

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
        if (prefs.enabled) showFloating()
        notifyUi()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // O serviço não lê conteúdo da tela.
    }

    override fun onInterrupt() {
        stop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        state = State.IDLE
        stroke = null
        finalizing = false
        stopRequested = false
        handler.removeCallbacksAndMessages(null)
        removePick()
        removeFloatingView()
        instance = null
        notifyUi()
    }

    private fun notifyUi() {
        uiListener?.invoke()
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    // ------------------------------------------------------------------ controle público

    fun start() {
        if (state == State.RUNNING) return
        if (stroke != null || finalizing) {
            // ainda encerrando o toque anterior: tenta de novo em instantes
            if (startRetries++ < 20) handler.postDelayed({ start() }, 150)
            return
        }
        startRetries = 0
        val (w, h) = screenSize()
        cx = if (prefs.pointX >= 0f) prefs.pointX.coerceIn(1f, w - 1f) else w / 2f
        cy = if (prefs.pointY >= 0f) prefs.pointY.coerceIn(1f, h - 1f) else h / 2f
        curX = cx
        curY = cy
        sign = 1f
        segCount = 0
        dirIndex = 0
        cancelCount = 0
        stopRequested = false
        state = State.RUNNING
        updateBubble()
        notifyUi()
        dispatchSegment()
    }

    fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
        if (stroke != null) stopRequested = true
        updateBubble()
        notifyUi()
    }

    fun resume() = start()

    fun stop() {
        if (state == State.IDLE) return
        state = State.IDLE
        if (stroke != null) stopRequested = true
        updateBubble()
        notifyUi()
    }

    fun togglePlay() {
        if (state == State.RUNNING) pause() else start()
    }

    // ------------------------------------------------------------------ motor do gesto

    private fun currentDir(): Int =
        if (prefs.direction == Dir.MIXED) dirIndex else prefs.direction // 0=H 1=V 2=D\ 3=D/

    private fun dispatchSegment() {
        val finalSeg = stopRequested
        val path = Path()
        path.moveTo(curX, curY)
        var dur = 10L
        var tx = curX
        var ty = curY

        if (!finalSeg) {
            val (w, h) = screenSize()
            val margin = dp(56) // evita as bordas (gesto de voltar / barra de status)
            val maxX = max(0f, min(cx, w - cx) - margin)
            val maxY = max(0f, min(cy, h - cy) - margin)
            val k = prefs.amplitude / 100f
            val dirNow = currentDir()
            val hx = if (dirNow == 0 || dirNow >= 2) maxX * k else 0f
            val hy = if (dirNow >= 1) maxY * k else 0f
            if (hx + hy < 2f) {
                fail("Sem espaço para mover. Escolha um ponto mais ao centro da tela.")
                return
            }
            val ySign = if (dirNow == 3) -1f else 1f
            tx = cx + sign * hx
            ty = cy + sign * hy * ySign
            val dist = hypot(tx - curX, ty - curY)
            if (dist < 1f) {
                tx = curX + 1f
            }
            path.lineTo(tx, ty)
            val pxPerSec = (150 + prefs.speed * 150) * resources.displayMetrics.density
            dur = (hypot(tx - curX, ty - curY) / pxPerSec * 1000f).toLong().coerceIn(30L, 20000L)
        }

        val willContinue = !finalSeg
        val s = try {
            stroke?.continueStroke(path, 0, dur, willContinue)
                ?: GestureDescription.StrokeDescription(path, 0, dur, willContinue)
        } catch (e: Exception) {
            fail("Não foi possível continuar o gesto.")
            return
        }
        stroke = s
        if (finalSeg) finalizing = true

        val ok = dispatchGesture(GestureDescription.Builder().addStroke(s).build(), callback, handler)
        if (!ok) {
            fail("O Android recusou o gesto. Verifique o serviço de acessibilidade.")
            return
        }
        if (!finalSeg) {
            curX = tx
            curY = ty
            sign = -sign
            segCount++
            if (prefs.direction == Dir.MIXED && segCount % 2 == 0) dirIndex = (dirIndex + 1) % 4
        }
    }

    private val callback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            cancelCount = 0
            if (finalizing) {
                // toque finalizado (agora sim o dedo virtual é levantado)
                finalizing = false
                stroke = null
                stopRequested = false
                return
            }
            dispatchSegment()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            val wasFinal = finalizing
            stroke = null
            finalizing = false
            if (wasFinal || stopRequested || state != State.RUNNING) {
                stopRequested = false
                return
            }
            cancelCount++
            if (cancelCount <= 3) {
                handler.postDelayed({
                    if (state == State.RUNNING && stroke == null) dispatchSegment()
                }, 150)
            } else {
                fail("O gesto foi cancelado pelo sistema. Tente novamente.")
            }
        }
    }

    private fun fail(msg: String) {
        state = State.IDLE
        stroke = null
        finalizing = false
        stopRequested = false
        toast(msg)
        updateBubble()
        notifyUi()
    }

    // ------------------------------------------------------------------ botão flutuante

    fun showFloating() {
        if (floatView != null) return
        val themed = ContextThemeWrapper(this, R.style.Theme_NewTouch)
        val v = LayoutInflater.from(themed).inflate(R.layout.overlay_floating, null)
        val (w, _) = screenSize()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = if (prefs.floatX >= 0) prefs.floatX else (w - dp(80)).toInt()
        lp.y = if (prefs.floatY >= 0) prefs.floatY else dp(220).toInt()
        floatParams = lp

        val bubble = v.findViewById<View>(R.id.bubble)
        val menu = v.findViewById<View>(R.id.menu)
        val btnPlay = v.findViewById<View>(R.id.btnPlayPause)
        val btnStop = v.findViewById<View>(R.id.btnStopAll)
        val btnApp = v.findViewById<View>(R.id.btnOpenApp)
        listOf(btnPlay, btnStop, btnApp).forEach { it.pressEffect() }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    bubble.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) {
                        prefs.floatX = lp.x
                        prefs.floatY = lp.y
                    } else {
                        setMenuOpen(!menuOpen)
                    }
                }
                MotionEvent.ACTION_CANCEL ->
                    bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            true
        }

        btnPlay.setOnClickListener {
            togglePlay()
            setMenuOpen(false)
        }
        btnStop.setOnClickListener {
            prefs.enabled = false
            hideFloating()
        }
        btnApp.setOnClickListener {
            setMenuOpen(false)
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            } catch (e: Exception) {
                toast("Abra o New Touch pela tela inicial.")
            }
        }

        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            toast("Não foi possível exibir o botão flutuante.")
            return
        }
        floatView = v
        floatingVisible = true
        menuOpen = false
        prefs.enabled = true
        updateBubble()
        notifyUi()
    }

    fun hideFloating() {
        stop()
        removeFloatingView()
        notifyUi()
    }

    private fun removeFloatingView() {
        ringAnim?.cancel()
        ringAnim = null
        floatView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        floatView = null
        floatParams = null
        floatingVisible = false
        menuOpen = false
    }

    private fun setMenuOpen(open: Boolean) {
        val v = floatView ?: return
        val lp = floatParams ?: return
        menuOpen = open
        val menu = v.findViewById<View>(R.id.menu)
        if (open) {
            menu.alpha = 0f
            menu.visibility = View.VISIBLE
            menu.animate().alpha(1f).setDuration(160).start()
            v.post {
                val (w, _) = screenSize()
                if (lp.x + v.width > w) {
                    lp.x = max(0, w - v.width)
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                }
            }
        } else {
            menu.visibility = View.GONE
        }
    }

    private fun updateBubble() {
        val v = floatView ?: return
        val play = v.findViewById<ImageView>(R.id.btnPlayPause)
        play.setImageResource(if (state == State.RUNNING) R.drawable.ic_pause else R.drawable.ic_play)
        val ring = v.findViewById<View>(R.id.ring)
        if (state == State.RUNNING) {
            if (ringAnim == null) {
                ringAnim = pulseAnimator(ring).also { it.start() }
            }
        } else {
            ringAnim?.cancel()
            ringAnim = null
            ring.alpha = 0f
            ring.scaleX = 1f
            ring.scaleY = 1f
        }
        v.findViewById<View>(R.id.bubble).alpha = if (state == State.PAUSED) 0.75f else 1f
    }

    // ------------------------------------------------------------------ escolher ponto

    fun startPick() {
        if (pickView != null) return
        val themed = ContextThemeWrapper(this, R.style.Theme_NewTouch)
        val v = LayoutInflater.from(themed).inflate(R.layout.overlay_pick, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        v.findViewById<View>(R.id.pickCancel).setOnClickListener { removePick() }
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                prefs.pointX = e.rawX
                prefs.pointY = e.rawY
                removePick()
                toast("Ponto definido")
                notifyUi()
            }
            true
        }
        try {
            wm.addView(v, lp)
            pickView = v
        } catch (e: Exception) {
            toast("Não foi possível abrir o seletor de ponto.")
        }
    }

    private fun removePick() {
        pickView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        pickView = null
    }
}
