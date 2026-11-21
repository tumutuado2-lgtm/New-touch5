package com.newtouch.app

import android.content.Context

/** Direções configuráveis. MIXED alterna entre todas automaticamente. */
object Dir {
    const val H = 0
    const val V = 1
    const val D = 2
    const val MIXED = 3
}

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("newtouch", Context.MODE_PRIVATE)

    var speed: Int
        get() = sp.getInt("speed", 5)
        set(v) { sp.edit().putInt("speed", v).apply() }

    var amplitude: Int
        get() = sp.getInt("amplitude", 60)
        set(v) { sp.edit().putInt("amplitude", v).apply() }

    var direction: Int
        get() = sp.getInt("direction", Dir.H)
        set(v) { sp.edit().putInt("direction", v).apply() }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var pointX: Float
        get() = sp.getFloat("pointX", -1f)
        set(v) { sp.edit().putFloat("pointX", v).apply() }

    var pointY: Float
        get() = sp.getFloat("pointY", -1f)
        set(v) { sp.edit().putFloat("pointY", v).apply() }

    var floatX: Int
        get() = sp.getInt("floatX", -1)
        set(v) { sp.edit().putInt("floatX", v).apply() }

    var floatY: Int
        get() = sp.getInt("floatY", -1)
        set(v) { sp.edit().putInt("floatY", v).apply() }

    fun clearPoint() {
        sp.edit().remove("pointX").remove("pointY").apply()
    }
}
