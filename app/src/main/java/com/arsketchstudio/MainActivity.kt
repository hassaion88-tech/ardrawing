
package com.arsketchstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(20, 20, 25)
        window.navigationBarColor = Color.rgb(20, 20, 25)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 18, 24))
        }

        val title = TextView(this).apply {
            text = "AR Sketch Studio"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "ارسم صورتك على الورق بسهولة"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 28)
        }

        statusText = TextView(this).apply {
            text = "أهلاً بيك! جهّز موبايلك فوق الورقة."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        }

        val startButton = Button(this).apply {
            text = "بدء الرسم بالكاميرا"
            textSize = 18f
            setOnClickListener {
                statusText.text =
                    "الخطوة الجاية راح نفعّل الكاميرا ونعرض الصورة للتتبع."
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(statusText)
        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }
}
