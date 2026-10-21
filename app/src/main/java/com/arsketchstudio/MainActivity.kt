
package com.arsketchstudio

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private val cameraRequestCode = 100

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
            text = "اضغط الزر للسماح باستخدام الكاميرا."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        }

        val startButton = Button(this).apply {
            text = "السماح وتشغيل الكاميرا"
            textSize = 18f
            setOnClickListener {
                requestCameraPermission()
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

    private fun requestCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            statusText.text = "إذن الكاميرا متوفر. راح نفعّل عرضها بالخطوة الجاية."
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == cameraRequestCode) {
            statusText.text = if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                "تم السماح بالكاميرا! جاهزين للخطوة الجاية."
            } else {
                "إذن الكاميرا مرفوض. اسمح بيه حتى نكمل."
            }
        }
    }
}
