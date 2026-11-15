package com.nho.upscaler

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

data class AppItem(
    val name: String,
    val pkg: String,
    val icon: Drawable,
    var isPinned: Boolean = false,
    var customScale: Int = 0
)

class MainActivity : Activity() {
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: BaseAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var previewTv: TextView
    private val pinnedPkgs = mutableSetOf<String>()
    private var selectedFactor = 125
    private var showPinnedOnly = false
    private var currentQuery = ""
    private var physW = 1440
    private var physH = 3168
    private var physDen = 624

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("upscaler_prefs", Context.MODE_PRIVATE)
        selectedFactor = prefs.getInt("saved_factor", 125)
        showPinnedOnly = prefs.getBoolean("show_pinned_only", false)

        if (handleIncomingIntent(intent)) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 102)
            }
        }

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
            setPadding(36, 42, 36, 20)
        }

        val title = TextView(this).apply {
            text = "⚡ GAME UPSCALER PRO"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
        }
        root.addView(title)

        val subTitle = TextView(this).apply {
            text = "Chạm tên game để chạy • Nhấn giữ để tạo Shortcut màn hình chính"
            textSize = 11.5f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 4, 0, 14)
        }
        root.addView(subTitle)

        // Khung hiển thị trực tiếp thông số Pixel & DPI thực tế
        previewTv = TextView(this).apply {
            text = "Đang đọc độ phân giải vật lý..."
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64FFDA"))
            setPadding(24, 18, 24, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182026"))
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#263238"))
            }
        }
        root.addView(previewTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 })

        // Hàng chọn 4 mức Upscale chung
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val scales = listOf(110 to "1.10x", 115 to "1.15x", 120 to "1.20x", 125 to "1.25x")
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
                    updatePreviewText()
                    adapter.notifyDataSetChanged()
                }
            }
            radioGroup.addView(rb)
        }
        root.addView(radioGroup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Nút Reset Khẩn Cấp
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
                    cancelActiveNotification()
                    Toast.makeText(context, "Đã Reset màn hình về gốc!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 })

        // Hàng Lọc "Chỉ hiện game đã ghim"
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 12)
        }
        val favCheck = CheckBox(this).apply {
            text = "⭐ Chỉ hiện Game đã ghim (Ẩn ứng dụng khác)"
            textSize = 13f
            setTextColor(Color.parseColor("#FFD600"))
            isChecked = showPinnedOnly
            setOnCheckedChangeListener { _, isChecked ->
                showPinnedOnly = isChecked
                prefs.edit().putBoolean("show_pinned_only", isChecked).apply()
                sortAndFilterApps()
            }
        }
        filterRow.addView(favCheck)
        root.addView(filterRow)

        // Ô tìm kiếm
        val searchBox = EditText(this).apply {
            hint = "🔍 Gõ tên game hoặc package để lọc nhanh..."
            setHintTextColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            setPadding(28, 20, 28, 20)
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
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 })

        // Danh sách Game
        val listView = ListView(this).apply {
            divider = null
            dividerHeight = 14
        }
        adapter = object : BaseAdapter() {
            override fun getCount() = filteredApps.size
            override fun getItem(pos: Int) = filteredApps[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                val item = filteredApps[pos]
                val effectiveScale = if (item.customScale > 0) item.customScale else selectedFactor

                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(22, 20, 22, 20)
                    background = GradientDrawable().apply {
                        setColor(if (item.isPinned) Color.parseColor("#1E2822") else Color.parseColor("#1A1D24"))
                        cornerRadius = 24f
                        if (item.isPinned) setStroke(2, Color.parseColor("#00E676"))
                    }
                }
                val img = ImageView(this@MainActivity).apply {
                    setImageDrawable(item.icon)
                    layoutParams = LinearLayout.LayoutParams(105, 105).apply { rightMargin = 24 }
                }
                val textCol = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val nameTv = TextView(this@MainActivity).apply {
                    text = item.name
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (item.isPinned) Color.parseColor("#00E676") else Color.WHITE)
                }
                val pkgTv = TextView(this@MainActivity).apply {
                    text = item.pkg
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                }
                textCol.addView(nameTv)
                textCol.addView(pkgTv)

                // Nút chọn mức Scale riêng cho từng game (Per-game profile badge)
                val scaleBadge = TextView(this@MainActivity).apply {
                    val labelStr = "${effectiveScale / 100}.${effectiveScale % 100}x"
                    text = if (item.customScale > 0) "🎯 $labelStr" else labelStr
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (item.customScale > 0) Color.parseColor("#00E676") else Color.parseColor("#B0BEC5"))
                    setPadding(20, 12, 20, 12)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#263238"))
                        cornerRadius = 14f
                    }
                    setOnClickListener {
                        cyclePerGameScale(item)
                    }
                }

                // Nút Ngôi sao Ghim
                val pinBtn = TextView(this@MainActivity).apply {
                    text = if (item.isPinned) "⭐" else "☆"
                    textSize = 20f
                    setTextColor(if (item.isPinned) Color.parseColor("#FFD600") else Color.parseColor("#616161"))
                    setPadding(20, 10, 8, 10)
                    setOnClickListener { togglePin(item) }
                }

                row.addView(img)
                row.addView(textCol)
                row.addView(scaleBadge)
                row.addView(pinBtn)

                row.setOnClickListener {
                    launchGameByPkg(item.pkg, item.name, effectiveScale)
                }
                row.setOnLongClickListener {
                    createPinnedShortcut(item, effectiveScale)
                    true
                }
                return row
            }
        }
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        updatePreviewText()
        if (checkShizuku()) fetchPhysicalDisplayInfo()
        loadInstalledApps()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && handleIncomingIntent(intent)) {
            finish()
        }
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
        val action = intent.getStringExtra("action_type") ?: return false
        if (action == "RESET_NOW") {
            if (checkShizuku()) {
                runShizukuScript("wm size reset; wm density reset")
                cancelActiveNotification()
                Toast.makeText(this, "✅ Đã khôi phục màn hình gốc!", Toast.LENGTH_SHORT).show()
            }
            return true
        } else if (action == "QUICK_LAUNCH") {
            val pkg = intent.getStringExtra("target_pkg") ?: return true
            val name = intent.getStringExtra("target_name") ?: pkg
            val savedCustom = prefs.getInt("scale_$pkg", 0)
            val factor = if (savedCustom > 0) savedCustom else prefs.getInt("saved_factor", 125)
            launchGameByPkg(pkg, name, factor)
            return true
        }
        return false
    }

    private fun cyclePerGameScale(item: AppItem) {
        val cycle = listOf(0, 110, 115, 120, 125)
        val nextIdx = (cycle.indexOf(item.customScale) + 1) % cycle.size
        item.customScale = cycle[nextIdx]
        prefs.edit().putInt("scale_${item.pkg}", item.customScale).apply()

        val msg = if (item.customScale == 0) {
            "Đã đặt ${item.name} dùng mức chung (${selectedFactor}%)"
        } else {
            "🎯 Đã khóa riêng mức ${item.customScale / 100}.${item.customScale % 100}x cho ${item.name}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        adapter.notifyDataSetChanged()
    }

    private fun createPinnedShortcut(item: AppItem, factor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = getSystemService(ShortcutManager::class.java)
            if (sm != null && sm.isRequestPinShortcutSupported) {
                val bmp = drawableToBitmap(item.icon)
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("action_type", "QUICK_LAUNCH")
                    putExtra("target_pkg", item.pkg)
                    putExtra("target_name", item.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val scaleStr = "${factor / 100}.${factor % 100}x"
                val info = ShortcutInfo.Builder(this, "upscale_${item.pkg}")
                    .setShortLabel("⚡ ${item.name}")
                    .setLongLabel("${item.name} ($scaleStr)")
                    .setIcon(Icon.createWithBitmap(bmp))
                    .setIntent(launchIntent)
                    .build()
                sm.requestPinShortcut(info, null)
                Toast.makeText(this, "📌 Đã gửi yêu cầu tạo Shortcut ngoài màn hình chính!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun fetchPhysicalDisplayInfo() {
        thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val cmd = "wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'; wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'"
                val proc = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
                proc.waitFor()
                if (lines.size >= 2) {
                    val parts = lines[0].split("x")
                    if (parts.size == 2) {
                        physW = parts[0].toIntOrNull() ?: 1440
                        physH = parts[1].toIntOrNull() ?: 3168
                    }
                    physDen = lines[1].toIntOrNull() ?: 624
                    runOnUiThread { updatePreviewText() }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updatePreviewText() {
        if (!::previewTv.isInitialized) return
        val newW = physW * selectedFactor / 100
        val newH = physH * selectedFactor / 100
        val newDen = physDen * selectedFactor / 100
        previewTv.text = "🖥️ Gốc: ${physW}x${physH} (${physDen} DPI)  ➔  Upscale: ${newW}x${newH} (${newDen} DPI)"
    }

    private fun showActiveNotification(gameName: String, factor: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upscaler_active_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Trạng thái Upscale", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val resetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("action_type", "RESET_NOW")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 99, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val scaleStr = "${factor / 100}.${factor % 100}x"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.ic_Dialog_Info)
            .setContentTitle("⚡ Đang kích SSAA $scaleStr cho $gameName")
            .setContentText("Chạm vào đây để Khôi phục màn hình gốc ngay lập tức")
            .setContentIntent(pi)
            .setOngoing(true)
        nm.notify(1001, builder.build())
    }

    private fun cancelActiveNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1001)
    }

    private fun togglePin(item: AppItem) {
        if (pinnedPkgs.contains(item.pkg)) {
            pinnedPkgs.remove(item.pkg)
            item.isPinned = false
        } else {
            pinnedPkgs.add(item.pkg)
            item.isPinned = true
        }
        prefs.edit().putStringSet("pinned_pkgs", HashSet(pinnedPkgs)).apply()
        sortAndFilterApps()
    }

    private fun sortAndFilterApps() {
        allApps.forEach {
            it.isPinned = pinnedPkgs.contains(it.pkg)
            it.customScale = prefs.getInt("scale_${it.pkg}", 0)
        }
        allApps.sortWith(compareByDescending<AppItem> { it.isPinned }.thenBy { it.name.lowercase() })

        val q = currentQuery.trim().lowercase()
        filteredApps.clear()
        val baseList = if (showPinnedOnly && q.isEmpty()) allApps.filter { it.isPinned } else allApps
        if (q.isEmpty()) {
            filteredApps.addAll(baseList)
        } else {
            filteredApps.addAll(baseList.filter { it.name.lowercase().contains(q) || it.pkg.lowercase().contains(q) })
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
                AppItem(
                    r.loadLabel(pm).toString(),
                    pkg,
                    r.loadIcon(pm),
                    pinnedPkgs.contains(pkg),
                    prefs.getInt("scale_$pkg", 0)
                )
            }.distinctBy { it.pkg }

            runOnUiThread {
                allApps.clear()
                allApps.addAll(temp)
                sortAndFilterApps()
            }
        }
    }

    private fun launchGameByPkg(pkg: String, gameName: String, factor: Int) {
        if (!checkShizuku()) return
        val scaleStr = "${factor / 100}.${factor % 100}x"
        Toast.makeText(this, "🚀 Đang kích $scaleStr cho $gameName...", Toast.LENGTH_SHORT).show()
        showActiveNotification(gameName, factor)

        val dl = "$"
        val script = "PHYS_RES=" + dl + "(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); " +
                "PHYS_DEN=" + dl + "(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); " +
                "W=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f1); " +
                "H=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f2); " +
                "NEW_W=" + dl + "(( W * " + factor + " / 100 )); " +
                "NEW_H=" + dl + "(( H * " + factor + " / 100 )); " +
                "NEW_DEN=" + dl + "(( PHYS_DEN * " + factor + " / 100 )); " +
                "wm size " + dl + "{NEW_W}x" + dl + "{NEW_H}; " +
                "wm density " + dl + "{NEW_DEN}; " +
                "sleep 1; " +
                "ACT=" + dl + "(cmd package resolve-activity --brief " + pkg + " | tr -d '\\r' | tail -n 1); " +
                "am start --user 0 -n \"" + dl + "ACT\" > /dev/null 2>&1; " +
                "nohup sh -c 'sleep 8; while pidof " + pkg + " > /dev/null; do sleep 3; done; wm size reset; wm density reset' >/dev/null 2>&1 &"

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