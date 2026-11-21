package com.newtouch.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import android.view.View

/** Animação de "afundar" ao pressionar um botão. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressEffect() {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

/** Abre outra tela com transição deslizante. */
fun Activity.go(cls: Class<*>) {
    startActivity(Intent(this, cls))
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
}

/** Anel que pulsa continuamente (indicador de funcionamento). */
fun pulseAnimator(v: View): ObjectAnimator =
    ObjectAnimator.ofPropertyValuesHolder(
        v,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.9f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.9f),
        PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f)
    ).apply {
        duration = 1100
        repeatCount = ObjectAnimator.INFINITE
    }
