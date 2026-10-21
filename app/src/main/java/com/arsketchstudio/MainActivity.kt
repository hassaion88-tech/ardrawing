
package com.arsketchstudio

import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var tabs: LinearLayout

    private var provider: ProcessCameraProvider? = null
    private var selectedBitmap: Bitmap? = null
    private var outlineBitmap: Bitmap? = null
    private var showingOutline = false
    private var opacity = 0.5f
    private var imageScale = 1f
    private var currentTab = "التتبع"

    private val picker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }

                if (bitmap != null) {
                    selectedBitmap = bitmap
                    outlineBitmap = null
                    showingOutline = false
                    showCurrentTab()
                } else {
                    Toast.makeText(
                        this, "تعذر قراءة الصورة", Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this, "تعذر فتح الصورة", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else status.text = "اسمح باستخدام الكاميرا لتشغيل التتبع"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.statusBarColor = Color.rgb(24, 26, 32)
        window.navigationBarColor = Color.rgb(24, 26, 32)

        buildScreen()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(24, 26, 32))
        }

        val heading = TextView(this).apply {
            text = "AR Sketch Studio"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
        }

        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf("التتبع", "التخطيط", "صوري").forEach { name ->
            val button = Button(this).apply {
                text = name
                setOnClickListener {
                    currentTab = name
                    showCurrentTab()
                }
            }
            tabs.addView(button, LinearLayout.LayoutParams(0, 52, 1f))
        }

        content = FrameLayout(this)

        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }

        status = TextView(this).apply {
            text = "اختَر صورة حتى تبدأ"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 6, 8, 6)
        }

        root.addView(heading)
        root.addView(tabs)
        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        root.addView(status)

        setContentView(root)
        showCurrentTab()
    }

    private fun showCurrentTab() {
        content.removeAllViews()

        when (currentTab) {
            "التتبع" -> showTracking()
            "التخطيط" -> showPlanning()
            "صوري" -> showPhotos()
        }
    }

    private fun showTracking() {
        content.addView(
            preview,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        image.visibility =
            if (selectedBitmap != null) View.VISIBLE else View.GONE

        selectedBitmap?.let { bitmap ->
            image.setImageBitmap(
                if (showingOutline) getOutline(bitmap) else bitmap
            )
            image.alpha = opacity
            image.scaleX = imageScale
            image.scaleY = imageScale

            content.addView(
                image,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
        }

        controls.addView(Button(this).apply {
            text = "اختيار صورة"
            setOnClickListener { picker.launch("image/*") }
        })

        val alpha = SeekBar(this).apply {
            max = 100
            progress = (opacity * 100).toInt()

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        opacity = progress / 100f
                        image.alpha = opacity
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        val zoom = SeekBar(this).apply {
            max = 150
            progress = ((imageScale - 0.5f) * 100)
                .toInt().coerceIn(0, 150)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        imageScale = 0.5f + progress / 100f
                        image.scaleX = imageScale
                        image.scaleY = imageScale
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        controls.addView(alpha, LinearLayout.LayoutParams(150, 48))
        controls.addView(zoom, LinearLayout.LayoutParams(150, 48))

        content.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        status.text = "التتبع: اختَر صورة واضبط شفافيتها وحجمها"
    }

    private fun showPlanning() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
        }

        val planImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(35, 37, 44))
        }

        selectedBitmap?.let { bitmap ->
            planImage.setImageBitmap(
                if (showingOutline) getOutline(bitmap) else bitmap
            )
        }

        column.addView(
            planImage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val buttons = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        buttons.addView(Button(this).apply {
            text = "اختيار صورة"
            setOnClickListener { picker.launch("image/*") }
        })

        buttons.addView(Button(this).apply {
            text = if (showingOutline) "الصورة الأصلية" else "تحويل إلى خطوط"

            setOnClickListener {
                if (selectedBitmap == null) {
                    picker.launch("image/*")
                } else {
                    showingOutline = !showingOutline
                    showCurrentTab()
                }
            }
        })

        buttons.addView(Button(this).apply {
            text = "حفظ التخطيط"

            setOnClickListener {
                val bitmap = selectedBitmap

                if (bitmap == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "اختَر صورة أولاً",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    saveBitmap(
                        if (showingOutline) getOutline(bitmap) else bitmap
                    )
                }
            }
        })

        column.addView(buttons)
        content.addView(column)

        status.text = "التخطيط: حوّل الصورة إلى خطوط أو احفظها"
    }

    private fun showPhotos() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        selectedBitmap?.let { photo.setImageBitmap(it) }

        column.addView(
            photo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        column.addView(Button(this).apply {
            text = "اختيار صورة من الهاتف"
            setOnClickListener { picker.launch("image/*") }
        })

        column.addView(Button(this).apply {
            text = "حفظ الصورة"
            setOnClickListener {
                selectedBitmap?.let { saveBitmap(it) }
                    ?: picker.launch("image/*")
            }
        })

        content.addView(column)
        status.text = "صوري: اختر صورة أو احفظ الصورة الحالية"
    }

    // تحويل الصورة إلى خطوط باستخدام اكتشاف الحواف Sobel
    private fun getOutline(source: Bitmap): Bitmap {
        outlineBitmap?.let { return it }

        val maxDimension = 1200f
        val scale = minOf(
            1f,
            maxDimension / source.width,
            maxDimension / source.height
        )

        val width = (source.width * scale).toInt().coerceAtLeast(3)
        val height = (source.height * scale).toInt().coerceAtLeast(3)

        val small = Bitmap.createScaledBitmap(
            source, width, height, true
        )

        val count = width * height
        val gray = IntArray(count)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = small.getPixel(x, y)

                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)

                gray[y * width + x] =
                    (299 * r + 587 * g + 114 * b) / 1000
            }
        }

        val edges = IntArray(count)
        var maxEdge = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x

                val gx =
                    -gray[i - width - 1] +
                    gray[i - width + 1] -
                    2 * gray[i - 1] +
                    2 * gray[i + 1] -
                    gray[i + width - 1] +
                    gray[i + width + 1]

                val gy =
                    -gray[i - width - 1] -
                    2 * gray[i - width] -
                    gray[i - width + 1] +
                    gray[i + width - 1] +
                    2 * gray[i + width] +
                    gray[i + width + 1]

                val strength = sqrt(
                    (gx.toDouble() * gx + gy.toDouble() * gy)
                ).toInt()

                edges[i] = strength
                if (strength > maxEdge) maxEdge = strength
            }
        }

        // حد أدنى للتفاصيل، مع مراعاة تباين الصورة
        val threshold = maxOf(65, (maxEdge * 0.10f).toInt())

        val pixels = IntArray(count) { Color.WHITE }

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val strength = edges[i]

                if (strength >= threshold) {
                    // خطوط داكنة وواضحة على خلفية بيضاء
                    val darkness = (
                        35 - ((strength - threshold) / 12)
                    ).coerceIn(0, 35)

                    pixels[i] = Color.rgb(
                        darkness, darkness, darkness
                    )
                } else {
                    pixels[i] = Color.WHITE
                }
            }
        }

        val result = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        )

        result.setPixels(
            pixels, 0, width, 0, 0, width, height
        )

        if (small !== source) small.recycle()

        outlineBitmap = result
        return result
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    "ARSketch_${System.currentTimeMillis()}.png"
                )
                put(
                    android.provider.MediaStore.Images.Media.MIME_TYPE,
                    "image/png"
                )
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/AR Sketch Studio"
                )
            }

            val uri = contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (uri != null) {
                val saved = contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                } ?: false

                if (saved) {
                    Toast.makeText(
                        this, "تم الحفظ بالصور", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this, "تعذر حفظ الصورة", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(
                this, "تعذر حفظ الصورة", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val cameraPreview = Preview.Builder().build().also {
                    it.surfaceProvider = preview.surfaceProvider
                }

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    cameraPreview
                )

                status.text = "الكاميرا جاهزة"
            } catch (_: Exception) {
                status.text = "تعذر تشغيل الكاميرا"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        provider?.unbindAll()
        super.onDestroy()
    }
}
