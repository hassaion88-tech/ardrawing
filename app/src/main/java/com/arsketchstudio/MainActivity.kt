
package com.arsketchstudio

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var opacityBar: SeekBar
    private lateinit var zoomBar: SeekBar

    private val cameraPermissionCode = 100
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildScreen()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 25))
        }

        val title = TextView(this).apply {
            text = "AR Sketch Studio"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }

        val cameraFrame = FrameLayout(this)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        cameraFrame.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        statusText = TextView(this).apply {
            text = "جاري تجهيز الكاميرا..."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 10, 8, 10)
        }

        val opacityLabel = TextView(this).apply {
            text = "شفافية صورة التتبع (راح نفعّلها لاحقاً)"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        opacityBar = SeekBar(this).apply {
            max = 100
            progress = 50
            isEnabled = false
        }

        val zoomLabel = TextView(this).apply {
            text = "حجم صورة التتبع (راح نفعّله لاحقاً)"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        zoomBar = SeekBar(this).apply {
            max = 200
            progress = 100
            isEnabled = false
        }

        val cameraButton = Button(this).apply {
            text = "إعادة تشغيل الكاميرا"
            setOnClickListener {
                startCamera()
            }
        }

        root.addView(title)
        root.addView(
            cameraFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(statusText)
        root.addView(opacityLabel)
        root.addView(
            opacityBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(zoomLabel)
        root.addView(
            zoomBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(cameraButton)

        setContentView(root)
    }

    private fun startCamera() {
        statusText.text = "جاري تشغيل الكاميرا..."

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )

                statusText.text =
                    "الكاميرا تعمل! خلي الموبايل ثابت فوق الورقة."

            } catch (e: Exception) {
                statusText.text = "تعذر تشغيل الكاميرا. حاول مرة ثانية."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == cameraPermissionCode) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            } else {
                statusText.text =
                    "لازم تسمح للتطبيق باستخدام الكاميرا."
            }
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
