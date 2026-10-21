
package com.arsketchstudio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val bg = Color.rgb(0, 0, 0)
    private val panel = Color.rgb(18, 18, 18)
    private val red = Color.rgb(255, 40, 65)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 155, 160)

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var image: ImageView
    private lateinit var status: TextView
    private lateinit var nav: LinearLayout

    private var provider: ProcessCameraProvider? = null
    private var selectedBitmap: Bitmap? = null
    private var outlineBitmap: Bitmap? = null

    private var currentTab = "التتبع"
    private var showingOutline = false
    private var opacity = 0.65f
    private var imageScale = 1f
    private var imageX = 0f
    private var imageY = 0f
    private var lineStrength = 100

    private val projectsDir: File
        get() = File(filesDir, "projects").apply { mkdirs() }

    private val picker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else status.text = "اسمح للتطبيق باستخدام الكاميرا"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = bg
        window.navigationBarColor = bg

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 14): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(48, 28, 32))
        }
    }

    private fun label(
        text: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun makeButton(
        text: String,
        primary: Boolean = false,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setTextColor(if (primary) white else red)
            background = rounded(
                if (primary) Color.rgb(125, 12, 28)
                else Color.rgb(28, 22, 24),
                12
            )
            setPadding(dp(5), 0, dp(5), 0)
            setOnClickListener { action() }
        }
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setBackgroundColor(bg)
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(label("AR SKETCH STUDIO", 18f, red, true))
        brand.addView(label("TRACE • DRAW • CREATE", 9f, muted))

        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("●", 12f, red, true))
        header.addView(label("  CAMERA READY", 10f, muted))

        content = FrameLayout(this).apply {
            background = rounded(panel, 16)
            clipChildren = true
        }

        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
        }

        status = label("الكاميرا جاهزة • اختَر صورة للرسم", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = rounded(Color.rgb(13, 13, 13), 14)
        }

        root.addView(header)

        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                setMargins(dp(8), dp(2), dp(8), dp(2))
            }
        )

        root.addView(
            status,
            LinearLayout.LayoutParams(-1, dp(24))
        )

        root.addView(
            nav,
            LinearLayout.LayoutParams(-1, dp(56)).apply {
                setMargins(dp(8), dp(1), dp(8), dp(5))
            }
        )

        setContentView(root)
        renderNavigation()
        showCurrentTab()
    }

    private fun renderNavigation() {
        nav.removeAllViews()

        val items = listOf(
            "التتبع" to "◎",
            "التخطيط" to "✎",
            "المعرض" to "▦",
            "المشاريع" to "▣"
        )

        items.forEach { (name, symbol) ->
            val active = currentTab == name

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(
                    if (active) Color.rgb(65, 12, 23)
                    else Color.TRANSPARENT,
                    10
                )
                setPadding(dp(2), dp(1), dp(2), dp(1))

                addView(label(
                    symbol, 19f, red, true
                ).apply {
                    gravity = Gravity.CENTER
                })

                addView(label(
                    name, 10f, if (active) white else red, active
                ).apply {
                    gravity = Gravity.CENTER
                })

                setOnClickListener {
                    currentTab = name
                    renderNavigation()
                    showCurrentTab()
                }
            }

            nav.addView(
                item,
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            )
        }
    }

    private fun showCurrentTab() {
        content.removeAllViews()

        when (currentTab) {
            "التتبع" -> showTracking()
            "التخطيط" -> showPlanning()
            "المعرض" -> showGallery()
            "المشاريع" -> showProjects()
        }
    }

    private fun loadImage(uri: Uri) {
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {
                toast("تعذر فتح الصورة")
                return
            }

            selectedBitmap = bitmap
            outlineBitmap = null
            showingOutline = false
            imageX = 0f
            imageY = 0f
            imageScale = 1f

            currentTab = "التتبع"
            renderNavigation()
            showCurrentTab()
            toast("تم اختيار الصورة")
        } catch (_: Exception) {
            toast("تعذر فتح الصورة")
        }
    }

    private fun showTracking() {
        content.addView(preview, FrameLayout.LayoutParams(-1, -1))

        selectedBitmap?.let { bitmap ->
            image.setImageBitmap(
                if (showingOutline) getOutline(bitmap) else bitmap
            )

            image.alpha = opacity
            image.scaleX = imageScale
            image.scaleY = imageScale
            image.translationX = imageX
            image.translationY = imageY

            image.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.tag = floatArrayOf(
                            event.rawX, event.rawY, imageX, imageY
                        )
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val start = view.tag as? FloatArray
                        if (start != null) {
                            imageX = start[2] + event.rawX - start[0]
                            imageY = start[3] + event.rawY - start[1]
                            view.translationX = imageX
                            view.translationY = imageY
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        view.performClick()
                        true
                    }

                    else -> true
                }
            }

            content.addView(image, FrameLayout.LayoutParams(-1, -1))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(8, 8, 8), 12)
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }

        controls.addView(makeButton("+ صورة", true) {
            picker.launch("image/*")
        })

        controls.addView(makeButton("إعدادات", false) {
            showTrackingSettings()
        })

        controls.addView(makeButton("خطوط", false) {
            selectedBitmap?.let {
                showingOutline = !showingOutline
                image.setImageBitmap(
                    if (showingOutline) getOutline(it) else it
                )
            } ?: picker.launch("image/*")
        })

        controls.addView(makeButton("حفظ", false) {
            saveProject()
        })

        content.addView(
            controls,
            FrameLayout.LayoutParams(-1, dp(46), Gravity.BOTTOM).apply {
                setMargins(dp(5), 0, dp(5), dp(5))
            }
        )

        if (selectedBitmap == null) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(10, 10, 10))
                addView(label("ابدأ الرسم", 24f, red, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(label(
                    "اختَر صورة حتى تظهر فوق الكاميرا",
                    13f, white
                ).apply {
                    gravity = Gravity.CENTER
                })
                addView(makeButton("اختيار صورة", true) {
                    picker.launch("image/*")
                })
            }

            content.addView(
                empty,
                FrameLayout.LayoutParams(
                    dp(250), dp(140), Gravity.CENTER
                )
            )
        }

        status.text = "حرّك الصورة بإصبعك • تحكم بالشفافية والحجم من الإعدادات"
    }

    private fun showTrackingSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(15, 12, 13), 14)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

        box.addView(label("إعدادات التتبع", 15f, red, true))
        box.addView(label("شفافية الصورة", 11f, white))

        box.addView(SeekBar(this).apply {
            max = 100
            progress = (opacity * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(red)
            thumbTintList = android.content.res.ColorStateList.valueOf(red)

            setOnSeekBarChangeListener(seekListener { value ->
                opacity = value / 100f
                image.alpha = opacity
            })
        })

        box.addView(label("حجم الصورة", 11f, white))

        box.addView(SeekBar(this).apply {
            max = 200
            progress = ((imageScale - 0.5f) * 100).toInt()
                .coerceIn(0, 200)
            progressTintList = android.content.res.ColorStateList.valueOf(red)
            thumbTintList = android.content.res.ColorStateList.valueOf(red)

            setOnSeekBarChangeListener(seekListener { value ->
                imageScale = 0.5f + value / 100f
                image.scaleX = imageScale
                image.scaleY = imageScale
            })
        })

        box.addView(makeButton("إغلاق", true) {
            showCurrentTab()
        })

        content.addView(
            box,
            FrameLayout.LayoutParams(
                dp(240), -2, Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dp(8), dp(8), 0)
            }
        )
    }

    private fun seekListener(
        action: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun showPlanning() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        column.addView(label("التخطيط والرسم", 20f, red, true))
        column.addView(label(
            "حوّل الصورة إلى خطوط واضحة للتتبع",
            12f, muted
        ))

        val planImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(Color.rgb(245, 245, 245), 10)
        }

        selectedBitmap?.let {
            planImage.setImageBitmap(
                if (showingOutline) getOutline(it) else it
            )
        }

        column.addView(
            planImage,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                setMargins(0, dp(6), 0, dp(6))
            }
        )

        column.addView(label("قوة الخطوط", 12f, white))

        column.addView(SeekBar(this).apply {
            max = 180
            progress = lineStrength
            progressTintList = android.content.res.ColorStateList.valueOf(red)
            thumbTintList = android.content.res.ColorStateList.valueOf(red)

            setOnSeekBarChangeListener(seekListener { value ->
                lineStrength = value.coerceAtLeast(1)
                outlineBitmap = null
                selectedBitmap?.let {
                    showingOutline = true
                    planImage.setImageBitmap(getOutline(it))
                }
            })
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        buttons.addView(makeButton("اختيار صورة", false) {
            picker.launch("image/*")
        })

        buttons.addView(makeButton("تحويل خطوط", true) {
            if (selectedBitmap == null) {
                picker.launch("image/*")
            } else {
                showingOutline = true
                planImage.setImageBitmap(getOutline(selectedBitmap!!))
            }
        })

        buttons.addView(makeButton("الصورة الأصلية", false) {
            selectedBitmap?.let {
                showingOutline = false
                planImage.setImageBitmap(it)
            }
        })

        buttons.addView(makeButton("حفظ PNG", false) {
            selectedBitmap?.let {
                saveBitmap(if (showingOutline) getOutline(it) else it)
            } ?: picker.launch("image/*")
        })

        column.addView(buttons)
        content.addView(column)
        status.text = "غيّر قوة الخطوط حسب الصورة"
    }

    private fun getOutline(source: Bitmap): Bitmap {
        outlineBitmap?.let { return it }

        val scale = minOf(
            1f,
            1000f / source.width,
            1000f / source.height
        )

        val width = (source.width * scale).toInt().coerceAtLeast(3)
        val height = (source.height * scale).toInt().coerceAtLeast(3)

        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val gray = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = small.getPixel(x, y)
                gray[y * width + x] = (
                    299 * Color.red(c) +
                    587 * Color.green(c) +
                    114 * Color.blue(c)
                ) / 1000
            }
        }

        val edges = IntArray(width * height)
        var maxEdge = 1

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x

                val gx =
                    -gray[i - width - 1] + gray[i - width + 1] -
                    2 * gray[i - 1] + 2 * gray[i + 1] -
                    gray[i + width - 1] + gray[i + width + 1]

                val gy =
                    -gray[i - width - 1] - 2 * gray[i - width] -
                    gray[i - width + 1] +
                    gray[i + width - 1] +
                    2 * gray[i + width] +
                    gray[i + width + 1]

                val magnitude = sqrt(
                    (gx.toDouble() * gx + gy.toDouble() * gy)
                ).toInt()

                edges[i] = magnitude
                maxEdge = max(maxEdge, magnitude)
            }
        }

        val threshold = (maxEdge * lineStrength / 220f)
            .toInt().coerceIn(18, 180)

        val pixels = IntArray(width * height) { Color.WHITE }

        for (i in edges.indices) {
            if (edges[i] > threshold) {
                pixels[i] = Color.BLACK
            }
        }

        val result = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        )

        result.setPixels(pixels, 0, width, 0, 0, width, height)

        if (small !== source) small.recycle()

        outlineBitmap = result
        return result
    }

    private fun showProjects() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        column.addView(label("مشاريعي", 21f, red, true))
        column.addView(label("المشاريع المحفوظة على هاتفك", 12f, muted))

        column.addView(makeButton("＋ حفظ المشروع الحالي", true) {
            saveProject()
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val files = projectsDir.listFiles()
            ?.filter { it.extension.lowercase() == "png" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (files.isEmpty()) {
            list.addView(label(
                "لا توجد مشاريع محفوظة حالياً",
                13f, muted
            ))
        }

        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 10)
                setPadding(dp(6), dp(3), dp(6), dp(3))
            }

            row.addView(
                label(file.nameWithoutExtension, 11f, white),
                LinearLayout.LayoutParams(0, dp(44), 1f)
            )

            row.addView(makeButton("فتح", true) {
                try {
                    selectedBitmap =
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    outlineBitmap = null
                    showingOutline = false
                    imageX = 0f
                    imageY = 0f
                    imageScale = 1f
                    currentTab = "التتبع"
                    renderNavigation()
                    showCurrentTab()
                    toast("تم فتح المشروع")
                } catch (_: Exception) {
                    toast("تعذر فتح المشروع")
                }
            })

            row.addView(makeButton("حذف", false) {
                file.delete()
                showCurrentTab()
                toast("تم حذف المشروع")
            })

            list.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            )
        }

        scroll.addView(list)
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(column)
        status.text = "تقدر تفتح المشاريع المحفوظة أو تحذفها"
    }

    private fun showGallery() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        column.addView(label("معرض الصور", 21f, red, true))
        column.addView(label(
            "اختَر صورة من الهاتف أو استخدم صورة محفوظة",
            12f, muted
        ))

        column.addView(makeButton("＋ اختيار صورة من الهاتف", true) {
            picker.launch("image/*")
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val files = projectsDir.listFiles()
            ?.filter { it.extension.lowercase() == "png" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (files.isEmpty()) {
            list.addView(label(
                "المعرض فارغ. اختَر صورة من هاتفك للبدء.",
                13f, muted
            ))
        }

        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 10)
                setPadding(dp(5), dp(3), dp(5), dp(3))
            }

            val thumb = ImageView(this).apply {
                setImageBitmap(
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            row.addView(thumb, LinearLayout.LayoutParams(dp(55), dp(55)))

            row.addView(
                label(file.nameWithoutExtension, 11f, white),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(6), 0, dp(3), 0)
                }
            )

            row.addView(makeButton("استخدم", true) {
                selectedBitmap =
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                outlineBitmap = null
                showingOutline = false
                currentTab = "التتبع"
                renderNavigation()
                showCurrentTab()
            })

            list.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            )
        }

        scroll.addView(list)
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(column)
        status.text = "اختَر صورة محفوظة أو افتح معرض الهاتف"
    }

    private fun saveProject() {
        val bitmap = selectedBitmap

        if (bitmap == null) {
            toast("اختَر صورة أولاً")
            picker.launch("image/*")
            return
        }

        try {
            val name = "Project_${System.currentTimeMillis()}.png"
            val file = File(projectsDir, name)

            FileOutputStream(file).use { output ->
                val toSave =
                    if (showingOutline) getOutline(bitmap) else bitmap
                toSave.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            getSharedPreferences("projects", MODE_PRIVATE).edit()
                .putFloat("${name}_opacity", opacity)
                .putFloat("${name}_scale", imageScale)
                .putFloat("${name}_x", imageX)
                .putFloat("${name}_y", imageY)
                .putBoolean("${name}_outline", showingOutline)
                .apply()

            toast("تم حفظ المشروع")
        } catch (_: Exception) {
            toast("تعذر حفظ المشروع")
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "ARSketch_${System.currentTimeMillis()}.png"
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/AR Sketch Studio"
                    )
                }
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (uri == null) {
                toast("تعذر حفظ الصورة")
                return
            }

            val saved = contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false

            toast(if (saved) "تم حفظ الصورة في الهاتف" else "تعذر حفظ الصورة")
        } catch (_: Exception) {
            toast("تعذر حفظ الصورة")
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

    private fun toast(message: String) {
        android.widget.Toast.makeText(
            this, message, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        provider?.unbindAll()
        super.onDestroy()
    }
}
