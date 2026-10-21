
package com.arsketchstudio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
import android.widget.Toast
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val bg = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(17, 25, 42)
    private val blue = Color.rgb(0, 190, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 174, 198)

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
        else status.text = "تحتاج تسمح للتطبيق باستخدام الكاميرا"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

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

    private fun rounded(color: Int, radius: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
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
            setTextColor(if (primary) bg else blue)
            background = rounded(
                if (primary) blue else Color.rgb(26, 40, 62), 14
            )
            setPadding(dp(8), 0, dp(8), 0)
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
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setBackgroundColor(bg)
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(label("AR SKETCH", 19f, blue, true))
        brand.addView(label("STUDIO  /  TRACE YOUR IDEAS", 9f, muted))

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(label("●", 12f, blue, true))
        header.addView(
            label("  STUDIO READY", 10f, muted),
            LinearLayout.LayoutParams(-2, -2)
        )

        content = FrameLayout(this).apply {
            background = rounded(panel, 18)
            clipToPadding = false
            clipChildren = false
        }

        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
        }

        status = label("اختَر صورة وابدأ الرسم", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(Color.rgb(13, 20, 35), 18)
        }

        root.addView(header)

        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                setMargins(dp(10), dp(2), dp(10), dp(2))
            }
        )

        root.addView(status)

        root.addView(
            nav,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
            ).apply {
                setMargins(dp(10), dp(2), dp(10), dp(6))
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
                background = if (active) rounded(
                    Color.rgb(20, 49, 73), 14
                ) else rounded(Color.TRANSPARENT, 14)

                setPadding(dp(4), dp(2), dp(4), dp(2))

                addView(label(
                    symbol, 20f, if (active) blue else muted, true
                ).apply {
                    gravity = Gravity.CENTER
                })

                addView(label(
                    name, 10f, if (active) blue else muted, active
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
        content.addView(
            preview,
            FrameLayout.LayoutParams(-1, -1)
        )

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

            content.addView(
                image,
                FrameLayout.LayoutParams(-1, -1)
            )
        } ?: run {
            val welcome = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(panel)
                addView(label("ابدأ فكرتك", 25f, blue, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(label(
                    "اختَر صورة حتى تظهر فوق الكاميرا",
                    13f, muted
                ).apply {
                    gravity = Gravity.CENTER
                })
                addView(makeButton("＋  اختيار صورة", true) {
                    picker.launch("image/*")
                })
            }

            content.addView(welcome, FrameLayout.LayoutParams(-1, -1))
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(9, 17, 30), 14)
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }

        controls.addView(makeButton("＋ صورة", true) {
            picker.launch("image/*")
        })

        controls.addView(makeButton("↺", false) {
            imageX = 0f
            imageY = 0f
            imageScale = 1f
            image.translationX = 0f
            image.translationY = 0f
            image.scaleX = 1f
            image.scaleY = 1f
        })

        controls.addView(makeButton("خطوط", false) {
            selectedBitmap?.let {
                showingOutline = !showingOutline
                image.setImageBitmap(
                    if (showingOutline) getOutline(it) else it
                )
            } ?: picker.launch("image/*")
        })

        controls.addView(makeButton("حفظ مشروع", false) {
            saveProject()
        })

        content.addView(
            controls,
            FrameLayout.LayoutParams(
                -1, dp(48), Gravity.BOTTOM
            ).apply {
                setMargins(dp(6), 0, dp(6), dp(6))
            }
        )

        status.text = "اسحب الصورة لتحريكها • الشفافية والتكبير بالأسفل"

        // إعدادات إضافية قابلة للفتح
        val settings = makeButton("⚙", false) {
            showTrackingSettings()
        }

        content.addView(
            settings,
            FrameLayout.LayoutParams(
                dp(52), dp(48), Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dp(8), dp(8), 0)
            }
        )
    }

    private fun showTrackingSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(10, 22, 40), 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        box.addView(label("إعدادات التتبع", 15f, blue, true))

        box.addView(label("شفافية الصورة", 11f, white))
        box.addView(SeekBar(this).apply {
            max = 100
            progress = (opacity * 100).toInt()
            setOnSeekBarChangeListener(seekListener { value ->
                opacity = value / 100f
                image.alpha = opacity
            })
        })

        box.addView(label("حجم الصورة", 11f, white))
        box.addView(SeekBar(this).apply {
            max = 200
            progress = ((imageScale - 0.5f) * 100)
                .toInt().coerceIn(0, 200)
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
                dp(250), -2, Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dp(60), dp(8), 0)
            }
        )
    }

    private fun showPlanning() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        column.addView(label("LINE ART LAB", 19f, blue, true))
        column.addView(label(
            "حوّل الصورة إلى رسم قابل للتتبع",
            12f, muted
        ))

        val planImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(Color.rgb(245, 247, 250), 12)
        }

        selectedBitmap?.let {
            planImage.setImageBitmap(
                if (showingOutline) getOutline(it) else it
            )
        }

        column.addView(
            planImage,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        )

        column.addView(label("قوة الخطوط", 12f, white))

        column.addView(SeekBar(this).apply {
            max = 180
            progress = lineStrength
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

        buttons.addView(makeButton(
            if (showingOutline) "الأصلية" else "تحويل إلى خطوط",
            true
        ) {
            if (selectedBitmap == null) {
                picker.launch("image/*")
            } else {
                showingOutline = !showingOutline
                planImage.setImageBitmap(
                    if (showingOutline) getOutline(selectedBitmap!!)
                    else selectedBitmap
                )
            }
        })

        buttons.addView(makeButton("حفظ PNG", false) {
            selectedBitmap?.let {
                saveBitmap(
                    if (showingOutline) getOutline(it) else it
                )
            } ?: picker.launch("image/*")
        })

        column.addView(buttons)
        content.addView(column)

        status.text = "عدّل قوة الخطوط حتى تحصل على النتيجة المناسبة"
    }

    private fun seekListener(
        action: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?, progress: Int, fromUser: Boolean
            ) {
                action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun getOutline(source: Bitmap): Bitmap {
        outlineBitmap?.let { return it }

        val scale = minOf(
            1f,
            1200f / source.width,
            1200f / source.height
        )

        val width = (source.width * scale).toInt().coerceAtLeast(3)
        val height = (source.height * scale).toInt().coerceAtLeast(3)

        val small = Bitmap.createScaledBitmap(
            source, width, height, true
        )

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

        val edge = IntArray(width * height)
        var maximum = 0

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

                val strength = sqrt(
                    (gx.toDouble() * gx + gy.toDouble() * gy)
                ).toInt()

                edge[i] = strength
                maximum = max(maximum, strength)
            }
        }

        val threshold = max(
            25,
            (maximum * lineStrength / 1000).coerceAtLeast(1)
        )

        val pixels = IntArray(width * height) { Color.WHITE }

        for (i in edge.indices) {
            if (edge[i] > threshold) {
                val darkness = (255 - edge[i] * 2)
                    .coerceIn(0, 65)
                pixels[i] = Color.rgb(
                    darkness, darkness, darkness
                )
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
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        column.addView(label("مشاريعي", 22f, blue, true))
        column.addView(label(
            "المشاريع المحفوظة داخل التطبيق",
            12f, muted
        ))

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
                "ماكو مشاريع محفوظة بعد. احفظ أول مشروع من التتبع.",
                13f, muted
            ))
        }

        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 12)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            val name = label(file.nameWithoutExtension, 12f, white)

            row.addView(name, LinearLayout.LayoutParams(0, dp(48), 1f))

            row.addView(makeButton("فتح", true) {
                try {
                    selectedBitmap = android.graphics.BitmapFactory
                        .decodeFile(file.absolutePath)
                    outlineBitmap = null
                    showingOutline = false
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
            })

            list.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            )
        }

        scroll.addView(list)
        column.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(column)
        status.text = "مشاريعك محفوظة محلياً على هذا الهاتف"
    }

    private fun showGallery() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        column.addView(label("معرض الصور", 22f, blue, true))
        column.addView(label(
            "الصور التي حفظها التطبيق في مجلد AR Sketch Studio",
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

        // اعرض مشاريع التطبيق المحفوظة، ولا تطلب صلاحية صور إضافية.
        if (files.isEmpty()) {
            list.addView(label(
                "المعرض فارغ حالياً. اختَر صورة من هاتفك أو احفظ مشروعاً.",
                13f, muted
            ))
        }

        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 12)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            val thumb = ImageView(this).apply {
                setImageBitmap(
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.rgb(35, 50, 70), 8)
            }

            row.addView(thumb, LinearLayout.LayoutParams(dp(58), dp(58)))

            row.addView(
                label(file.nameWithoutExtension, 11f, white),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(8), 0, dp(4), 0)
                }
            )

            row.addView(makeButton("استخدم", true) {
                selectedBitmap = android.graphics.BitmapFactory
                    .decodeFile(file.absolutePath)
                outlineBitmap = null
                showingOutline = false
                currentTab = "التتبع"
                renderNavigation()
                showCurrentTab()
            })

            list.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            )
        }

        scroll.addView(list)

        column.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        content.addView(column)
        status.text = "تقدر تفتح أي مشروع محفوظ وتستخدمه للتتبع"
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

            FileOutputStream(file).use {
                (if (showingOutline) getOutline(bitmap) else bitmap)
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            getSharedPreferences("projects", MODE_PRIVATE)
                .edit()
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
            ) ?: run {
                toast("تعذر حفظ الصورة")
                return
            }

            val saved = contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false

            if (saved) toast("تم حفظ الصورة في الهاتف")
            else toast("تعذر حفظ الصورة")
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        provider?.unbindAll()
        super.onDestroy()
    }
}
