package com.nho.upscaler

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

data class AppItem(val name: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: BaseAdapter
    private var selectedFactor = 125

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101216"))
            setPadding(36, 48, 36, 24)
        }

        val title = TextView(this).apply {
            text = "⚡ GAME UPSCALER (SHIZUKU)"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
        }
        root.addView(title)

        val subTitle = TextView(this).apply {
            text = "Chạm vào game để tự động kích SSAA + 144Hz và tự Reset khi thoát"
            textSize = 12f
            setTextColor(null)
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 6, 0, 24)
        }
        root.addView(subTitle)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        val scales = listOf(115 to "1.15x (Mát)", 120 to "1.20x (Vừa)", 125 to "1.25x (Chuẩn)")
        scales.forEach { (factor, label) ->
            val rb = RadioButton(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                id = factor
                isChecked = (factor == 125)
                setOnClickListener { selectedFactor = factor }
            }
            radioGroup.addView(rb)
        }
        root.addView(radioGroup)

        val resetBtn = Button(this).apply {
            text = "🔄 RESET MÀN HÌNH VỀ MẶC ĐỊNH NGAY"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D32F2F"))
                cornerRadius = 20f
            }
            setOnClickListener {
                if (checkShizuku()) {
                    runShizukuScript("wm size reset; wm density reset; settings put system min_refresh_rate 1.0; settings put global vivo_screen_refresh_rate_mode 0")
                    Toast.makeText(context, "Đã Reset màn hình về gốc!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 })

        val searchBox = EditText(this).apply {
            hint = "🔍 Gõ tên game hoặc package để lọc nhanh..."
            setHintTextColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2229"))
                cornerRadius = 20f
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { filterList(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 })

        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 16
        }
        adapter = object : BaseAdapter() {
            override fun getCount() = filteredApps.size
            override fun getItem(pos: Int) = filteredApps[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val item = filteredApps[pos]
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(24, 24, 24, 24)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1A1D24"))
                        cornerRadius = 24f
                    }
                }
                val img = ImageView(this@MainActivity).apply {
                    setImageDrawable(item.icon)
                    layoutParams = LinearLayout.LayoutParams(110, 110).apply { rightMargin = 28 }
                }
                val textCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val nameTv = TextView(this@MainActivity).apply {
                    text = item.name
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                }
                val pkgTv = TextView(this@MainActivity).apply {
                    text = item.pkg
                    textSize = 12f
                    setTextColor(Color.parseColor("#888888"))
                }
                textCol.addView(nameTv)
                textCol.addView(pkgTv)
                row.addView(img)
                row.addView(textCol)

                row.setOnClickListener { launchGameWithUpscale(item) }
                return row
            }
        }
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        checkShizuku()
        loadInstalledApps()
    }

    private fun checkShizuku(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "⚠️ Hãy bật Shizuku trước!", Toast.LENGTH_LONG).show()
                false
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
                false
            } else true
        } catch (e: Exception) {
            Toast.makeText(this, "⚠️ Chưa kết nối được Shizuku!", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun loadInstalledApps() {
        thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolves = pm.queryIntentActivities(intent, 0)
            val temp = resolves.mapNotNull { r ->
                val pkg = r.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                AppItem(r.loadLabel(pm).toString(), pkg, r.loadIcon(pm))
            }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(temp)
                filterList("")
            }
        }
    }

    private fun filterList(query: String) {
        val q = query.trim().lowercase()
        filteredApps.clear()
        if (q.isEmpty()) filteredApps.addAll(allApps)
        else filteredApps.addAll(allApps.filter { it.name.lowercase().contains(q) || it.pkg.lowercase().contains(q) })
        adapter.notifyDataSetChanged()
    }

    private fun launchGameWithUpscale(app: AppItem) {
        if (!checkShizuku()) return
        Toast.makeText(this, "🚀 Đang kích ${selectedFactor}% cho ${app.name}...", Toast.LENGTH_SHORT).show()

        val dl = "$"
        val script = "PHYS_RES=" + dl + "(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); " +
                "PHYS_DEN=" + dl + "(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); " +
                "W=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f1); " +
                "H=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f2); " +
                "NEW_W=" + dl + "(( W * " + selectedFactor + " / 100 )); " +
                "NEW_H=" + dl + "(( H * " + selectedFactor + " / 100 )); " +
                "NEW_DEN=" + dl + "(( PHYS_DEN * " + selectedFactor + " / 100 )); " +
                "wm size " + dl + "{NEW_W}x" + dl + "{NEW_H}; " +
                "wm density " + dl + "{NEW_DEN}; " +
                "settings put system peak_refresh_rate 144.0; " +
                "settings put system min_refresh_rate 144.0; " +
                "settings put global vivo_screen_refresh_rate_mode 144; " +
                "sleep 1; " +
                "ACT=" + dl + "(cmd package resolve-activity --brief " + app.pkg + " | tr -d '\\r' | tail -n 1); " +
                "am start --user 0 -n \"" + dl + "ACT\" > /dev/null 2>&1; " +
                "nohup sh -c 'sleep 8; while pidof " + app.pkg + " > /dev/null; do sleep 3; done; wm size reset; wm density reset; settings put system min_refresh_rate 1.0; settings put global vivo_screen_refresh_rate_mode 0' >/dev/null 2>&1 &"

        runShizukuScript(script)
    }

    private fun runShizukuScript(cmd: String) {
        thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                proc.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
