package com.hybrid.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView

/**
 * Launcher híbrido:
 *  - Pantalla de inicio estilo iPhone: reloj, iconos "squircle", dock flotante de cristal.
 *  - Deslizar hacia arriba: cajón de apps estilo Samsung One UI con buscador redondeado.
 */
class MainActivity : Activity() {

    private class App(val label: String, val icon: Drawable, val intent: Intent)

    private var apps: List<App> = emptyList()
    private var dockApps: List<App> = emptyList()

    private lateinit var homeGrid: GridLayout
    private lateinit var dockBar: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerGrid: GridLayout
    private lateinit var search: EditText
    private lateinit var detector: GestureDetector
    private var iconSize = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iconSize = dp(58)

        val root = FrameLayout(this)

        // ---------- HOME (iPhone) ----------
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(44), dp(12), dp(16))
        }
        home.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 60f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(8f, 0f, 2f, Color.argb(120, 0, 0, 0))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        home.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(6f, 0f, 1f, Color.argb(120, 0, 0, 0))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(24) })

        homeGrid = GridLayout(this).apply { columnCount = 4 }
        home.addView(homeGrid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        home.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        dockBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(70, 255, 255, 255))
                cornerRadius = dp(32).toFloat()
            }
        }
        home.addView(dockBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(home, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // ---------- CAJÓN (Samsung One UI) ----------
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 18, 18, 22))
            setPadding(dp(12), dp(48), dp(12), 0)
            visibility = View.GONE
        }
        search = EditText(this).apply {
            hint = "Buscar apps"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            isSingleLine = true
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255))
                cornerRadius = dp(26).toFloat()
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = fillDrawer()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        drawer.addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
        drawerGrid = GridLayout(this).apply { columnCount = 4 }
        drawerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(drawerGrid)
        }
        drawer.addView(drawerScroll, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)

        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                if (dy < -dp(120) && vy < -800 && drawer.visibility != View.VISIBLE) {
                    showDrawer(); return true
                }
                if (dy > dp(120) && vy > 800 && drawer.visibility == View.VISIBLE && drawerScroll.scrollY == 0) {
                    hideDrawer(); return true
                }
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (drawer.visibility == View.VISIBLE) hideDrawer()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) hideDrawer()
        // En un launcher, "atrás" en el inicio no hace nada.
    }

    // ---------- datos ----------
    private fun refresh() {
        loadApps()
        fill(homeGrid, apps.take(20))
        dockBar.removeAllViews()
        dockApps.forEach { dockBar.addView(tile(it, false), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        fillDrawer()
    }

    private fun loadApps() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                App(
                    ri.loadLabel(pm).toString(),
                    rounded(ri.loadIcon(pm)),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .sortedBy { it.label.lowercase() }

        val candidates = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        dockApps = candidates.mapNotNull { i ->
            pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()?.let { ri ->
                App(
                    ri.loadLabel(pm).toString(),
                    rounded(ri.loadIcon(pm)),
                    Intent(i).setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ---------- UI helpers ----------
    private fun fillDrawer() {
        val q = search.text.toString().trim().lowercase()
        fill(drawerGrid, if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) })
    }

    private fun fill(grid: GridLayout, list: List<App>) {
        grid.removeAllViews()
        val w = (resources.displayMetrics.widthPixels - dp(24)) / grid.columnCount
        list.forEach { grid.addView(tile(it, true), GridLayout.LayoutParams().apply { width = w }) }
    }

    private fun tile(app: App, showLabel: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
        addView(ImageView(context).apply { setImageDrawable(app.icon) },
            LinearLayout.LayoutParams(iconSize, iconSize))
        if (showLabel) {
            addView(TextView(context).apply {
                text = app.label
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
                setPadding(dp(2), dp(4), dp(2), 0)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setOnClickListener {
            try {
                startActivity(Intent(app.intent))
            } catch (_: Exception) {
            }
        }
    }

    /** Recorta el icono con la forma "squircle" de iOS. */
    private fun rounded(d: Drawable): Drawable {
        val size = dp(116)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size * 0.225f
        c.clipPath(Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, Path.Direction.CW)
        })
        c.drawColor(Color.argb(255, 40, 40, 46))
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return BitmapDrawable(resources, bmp)
    }

    private fun showDrawer() {
        search.setText("")
        drawerScroll.scrollTo(0, 0)
        drawer.translationY = resources.displayMetrics.heightPixels.toFloat()
        drawer.visibility = View.VISIBLE
        drawer.animate().translationY(0f).setDuration(250).start()
    }

    private fun hideDrawer() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        drawer.animate()
            .translationY(resources.displayMetrics.heightPixels.toFloat())
            .setDuration(220)
            .withEndAction { drawer.visibility = View.GONE }
            .start()
    }
}
