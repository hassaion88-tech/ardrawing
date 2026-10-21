package com.arsketchstudio

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var drawingImage: ImageView
    private lateinit var statusText: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageScale = 1f

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            drawingImage.setImageURI(uri)
            drawingImage.visibility = ImageView.VISIBLE
            updateImage()
            statusText.text = "تم اختيار الصورة. اضبطها فوق الورقة."
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = "اسمح باستخدام الكاميرا حتى تشتغل."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 22, 28))
        }

        val title = TextView(this).apply {
            text = "AR Sketch Studio"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 6, 8, 6)
        }

        val cameraFrame = FrameLayout(this)

        previewView = PreviewView(this).apply {
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        drawingImage = ImageView(this).apply {
            visibility = ImageView.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }

        cameraFrame.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        cameraFrame.addView(
            drawingImage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        statusText = TextView(this).apply {
            text = "جاري تجهيز الكاميرا..."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 4)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 2, 8, 2)
        }

        val chooseButton = Button(this).apply {
            text = "اختيار صورة"
            setOnClickListener {
                imagePicker.launch("image/*")
            }
        }

        val opacityControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val opacityLabel = TextView(this).apply {
            text = "الشفافية"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val opacityBar = SeekBar(this).apply {
            max = 100
            progress = 50

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        drawingImage.alpha = progress / 100f
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        opacityControls.addView(opacityLabel)
        opacityControls.addView(
            opacityBar,
            LinearLayout.LayoutParams(
                180,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val zoomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val zoomLabel = TextView(this).apply {
            text = "حجم الصورة"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val zoomBar = SeekBar(this).apply {
            max = 150
            progress = 50

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        imageScale = 0.5f + progress / 100f
                        updateImage()
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        zoomControls.addView(zoomLabel)
        zoomControls.addView(
            zoomBar,
            LinearLayout.LayoutParams(
                180,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        controls.addView(chooseButton)
        controls.addView(opacityControls)
        controls.addView(zoomControls)

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
        root.addView(controls)

        setContentView(root)
    }

    private fun updateImage() {
        drawingImage.scaleX = imageScale
        drawingImage.scaleY = imageScale
    }

    private fun startCamera() {
        statusText.text = "جاري تشغيل الكاميرا..."

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()

                preview.surfaceProvider =
                    previewView.surfaceProvider

                provider.unbindAll()

                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA

                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )

                statusText.text =
                    "الكاميرا تعمل. اختار صورة حتى تبدأ التتبع."

            } catch (e: Exception) {
                statusText.text =
                    "تعذر تشغيل الكاميرا: ${e.message ?: "خطأ غير معروف"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
