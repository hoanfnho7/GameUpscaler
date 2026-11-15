package com.nho.upscaler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

data class AppItem(val name: String, val pkg: String, val icon: Drawable, var isPinned: Boolean = false)

class MainActivity : Activity() {
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: BaseAdapter
    private lateinit var prefs: SharedPreferences
    private val pinnedPkgs = mutableSetOf<String>()
    private var selectedFactor = 125
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        selectedFactor = prefs.getInt("saved_factor", 125)

        val defaultPins = setOf(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.vng.pubgmobile",
            "com.tencent.ig"
        )
        val savedSet = prefs.getStringSet("pinned_pkgs", null)
        if (savedSet == null) {
            pinnedPkgs.addAll(defaultPins)
            prefs.edit().putStringSet("pinned_pkgs", HashSet(pinnedPkgs)).apply()
        } else {
            pinnedPkgs.addAll(savedSet)
        }

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
            text = "Chạm tên game để kích SSAA • Bấm ⭐ để ghim lên đầu"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 6, 0, 20)
        }
        root.addView(subTitle)

        // Hàng chọn 4 mức Upscale: 1.10x - 1.15x - 1.20x - 1.25x
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        val scales = listOf(
            110 to "1.10x",
            115 to "1.15x",
            120 to "1.20x",
            125 to "1.25x"
        )
        scales.forEach { (factor, label) ->
            val rb = RadioButton(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                id = factor
                isChecked = (factor == selectedFactor)
                layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    selectedFactor = factor
                    prefs.edit().putInt("saved_factor", factor).apply()
                }
            }
            radioGroup.addView(rb)
        }
        root.addView(radioGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val resetBtn = Button(this).apply {
            text = "🔄 RESET MÀN HÌNH VỀ MẶC ĐỊNH NGAY"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D32F2F"))
                cornerRadius = 20f
            }
            setOnClickListener {
                if (checkShizuku()) {
                    runShizukuScript("wm size reset; wm density reset")
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
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString() ?: ""
                    sortAndFilterApps()
                }
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
                        setColor(if (item.isPinned) Color.parseColor("#1E2822") else Color.parseColor("#1A1D24"))
                        cornerRadius = 24f
                        if (item.isPinned) {
                            setStroke(2, Color.parseColor("#00E676"))
                        }
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
                    setTextColor(if (item.isPinned) Color.parseColor("#00E676") else Color.WHITE)
                }
                val pkgTv = TextView(this@MainActivity).apply {
                    text = item.pkg
                    textSize = 12f
                    setTextColor(Color.parseColor("#888888"))
                }
                textCol.addView(nameTv)
                textCol.addView(pkgTv)

                val pinBtn = TextView(this@MainActivity).apply {
                    text = if (item.isPinned) "⭐" else "☆"
                    textSize = 22f
                    setTextColor(if (item.isPinned) Color.parseColor("#FFD600") else Color.parseColor("#616161"))
                    setPadding(24, 12, 12, 12)
                    setOnClickListener {
                        togglePin(item)
                    }
                }

                row.addView(img)
                row.addView(textCol)
                row.addView(pinBtn)

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

    private fun togglePin(item: AppItem) {
        if (pinnedPkgs.contains(item.pkg)) {
            pinnedPkgs.remove(item.pkg)
            item.isPinned = false
            Toast.makeText(this, "Đã bỏ ghim ${item.name}", Toast.LENGTH_SHORT).show()
        } else {
            pinnedPkgs.add(item.pkg)
            item.isPinned = true
            Toast.makeText(this, "⭐ Đã ghim ${item.name} lên đầu!", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("pinned_pkgs", HashSet(pinnedPkgs)).apply()
        sortAndFilterApps()
    }

    private fun sortAndFilterApps() {
        allApps.forEach { it.isPinned = pinnedPkgs.contains(it.pkg) }
        allApps.sortWith(compareByDescending<AppItem> { it.isPinned }.thenBy { it.name.lowercase() })

        val q = currentQuery.trim().lowercase()
        filteredApps.clear()
        if (q.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            filteredApps.addAll(allApps.filter { it.name.lowercase().contains(q) || it.pkg.lowercase().contains(q) })
        }
        adapter.notifyDataSetChanged()
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
                AppItem(r.loadLabel(pm).toString(), pkg, r.loadIcon(pm), pinnedPkgs.contains(pkg))
            }.distinctBy { it.pkg }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(temp)
                sortAndFilterApps()
            }
        }
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
                "sleep 1; " +
                "ACT=" + dl + "(cmd package resolve-activity --brief " + app.pkg + " | tr -d '\\r' | tail -n 1); " +
                "am start --user 0 -n \"" + dl + "ACT\" > /dev/null 2>&1; " +
                "nohup sh -c 'sleep 8; while pidof " + app.pkg + " > /dev/null; do sleep 3; done; wm size reset; wm density reset' >/dev/null 2>&1 &"

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