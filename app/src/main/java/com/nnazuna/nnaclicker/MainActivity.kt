package com.nnazuna.nnaclicker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {

    companion object {
        private const val REQ_EXPORT = 11
        private const val REQ_IMPORT = 12
        private val SPEEDS = floatArrayOf(0.5f, 1f, 1.5f, 2f, 3f)
    }

    private lateinit var storage: Storage
    private lateinit var k: Kit
    private val p get() = k.p

    // status
    private lateinit var a11yDot: View
    private lateinit var a11ySub: TextView
    private lateinit var a11yAction: TextView
    private lateinit var shzDot: View
    private lateinit var shzSub: TextView
    private lateinit var shzSwitch: Switch
    private lateinit var shzAction: TextView
    private lateinit var shzDiag: TextView
    private lateinit var panelButton: TextView

    // daftar & pengaturan
    private lateinit var listGroup: LinearLayout
    private lateinit var loopsValue: TextView
    private lateinit var loopMinus: TextView
    private lateinit var speedBox: LinearLayout
    private lateinit var intervalValue: TextView
    private lateinit var startValue: TextView
    private lateinit var limitValue: TextView
    private lateinit var jitterPxValue: TextView
    private lateinit var jitterPctValue: TextView
    private lateinit var dimLevelRow: View
    private lateinit var dimLevelValue: TextView

    /** Playback options being shown: the selected recording's own, or the global ones. */
    private var cfg = PlaySettings()
    private var cfgRec: Recording? = null
    private lateinit var scopeTitle: TextView
    private lateinit var scopeSub: TextView
    private lateinit var scopeSwitch: Switch
    private var suppressScope = false

    private var exportId: String? = null
    private var dimDivider: View? = null
    private var suppressSwitch = false
    private var listKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = Storage(this)
        k = Kit(this)
        Ui.styleWindow(this, p)
        ShizukuBridge.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(16f), k.dp(12f), k.dp(16f), k.dp(56f))
        }

        // ---- Masthead ----
        val headerBg = if (p.dark) p.surface else p.ink
        val headerInk = if (p.dark) p.ink else p.bg
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(headerBg)
            setPadding(k.dp(16f), k.dp(26f), k.dp(16f), k.dp(18f))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(View(this).apply { background = k.rounded(p.play, 0f) },
            LinearLayout.LayoutParams(k.dp(10f), k.dp(10f)).apply { rightMargin = k.dp(12f) })
        titleRow.addView(k.text("NNA Clicker", 34f, 3, headerInk), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(k.text("v${BuildConfig.VERSION_NAME}", 13f, 1, headerInk).apply { alpha = 0.7f })
        header.addView(titleRow)

        // ---- Kesiapan ----
        val status = k.group()
        status.addView(statusRow("无障碍服务").also {
            a11yDot = it.dot; a11ySub = it.sub; a11yAction = it.action
            a11yAction.text = "›"
            a11yAction.textSize = 22f
            a11yAction.contentDescription = "打开无障碍设置"
            a11yAction.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }.view)
        status.addView(k.divider(40f))
        val shz = statusRow("Shizuku").also {
            shzDot = it.dot; shzSub = it.sub; shzAction = it.action
            shzAction.setOnClickListener {
                if (!ShizukuBridge.hasPermission()) ShizukuBridge.requestPermission() else ShizukuBridge.reconnect()
                refresh()
            }
        }
        shzSwitch = Switch(this).apply {
            k.tintSwitch(this)
            setOnCheckedChangeListener { _, checked ->
                if (suppressSwitch) return@setOnCheckedChangeListener
                storage.useShizuku = checked
                if (checked) {
                    if (ShizukuBridge.hasPermission()) ShizukuBridge.connect() else ShizukuBridge.requestPermission()
                } else {
                    ShizukuBridge.disconnect()
                }
                refresh()
            }
        }
        (shz.view as LinearLayout).addView(shzSwitch)
        status.addView(shz.view)
        shzDiag = k.text("", 12.5f, 0, p.muted).apply {
            setPadding(k.dp(40f), 0, k.dp(16f), k.dp(12f))
            setTextIsSelectable(true)
        }
        status.addView(shzDiag)
        root.addView(status)

        panelButton = k.pillButton("显示面板", p.ink) {
            val s = RecorderService.instance ?: run {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return@pillButton
            }
            if (s.isPanelShown) s.hidePanel() else {
                s.showPanel()
                moveTaskToBack(true)
            }
            refresh()
        }
        root.addView(panelButton)
        root.addView(quickActions())

        // ---- Rekaman ----
        root.addView(k.sectionHeader("录制", "导入") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                REQ_IMPORT,
            )
        })
        listGroup = k.group()
        root.addView(listGroup)

        // ---- Pemutaran ----
        root.addView(k.sectionHeader("播放"))
        val play = k.group()

        // Which recording these options are for.
        val scopeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = k.dp(64f)
            setPadding(k.dp(16f), k.dp(10f), k.dp(12f), k.dp(10f))
        }
        val scopeCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scopeTitle = k.text("", 16f, 1)
        scopeSub = k.text("", 13f, 0, p.muted).apply { setPadding(0, k.dp(3f), 0, 0) }
        scopeCol.addView(scopeTitle)
        scopeCol.addView(scopeSub)
        scopeRow.addView(scopeCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        scopeSwitch = Switch(this).apply {
            k.tintSwitch(this)
            setOnCheckedChangeListener { _, on -> if (!suppressScope) setOwnSettings(on) }
        }
        scopeRow.addView(scopeSwitch)
        scopeRow.setOnClickListener { if (scopeSwitch.isEnabled) scopeSwitch.toggle() }
        play.addView(scopeRow)
        play.addView(k.divider())

        // Ulangi: stepper
        val loopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = k.dp(56f)
            setPadding(k.dp(16f), k.dp(6f), k.dp(8f), k.dp(6f))
        }
        val loopLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        loopLeft.addView(k.text("重复", 16f, 1))
        loopRow.addView(loopLeft, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        loopRow.addView(stepButton("−") { cfg.loops = (cfg.loops - 1).coerceAtLeast(0); saveCfg() }.also { loopMinus = it })
        loopsValue = k.text("", 18f, 2).apply {
            gravity = Gravity.CENTER
            minWidth = k.dp(44f)
            fontFeatureSettings = "tnum"
            setOnClickListener {
                Ui.numberDialog(this@MainActivity, p, "重复", cfg.loops.toString(), false,
                    "0 = 无限") { v -> v.toIntOrNull()?.let { cfg.loops = it }; saveCfg() }
            }
        }
        loopRow.addView(loopsValue)
        loopRow.addView(stepButton("+") { cfg.loops = cfg.loops + 1; saveCfg() })
        play.addView(loopRow)
        play.addView(k.divider())

        // Kecepatan: segmen
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(16f), k.dp(14f), k.dp(16f), k.dp(14f))
        }
        speedRow.addView(k.text("速度", 16f, 1))
        speedBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = k.rounded(p.bg, 4f)
            setPadding(k.dp(3f), k.dp(3f), k.dp(3f), k.dp(3f))
        }
        speedRow.addView(speedBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = k.dp(10f)
        })
        play.addView(speedRow)
        play.addView(k.divider())

        intervalValue = addRow(play, "循环间延时") {
            Ui.numberDialog(this, p, "循环间延时（秒）", (cfg.intervalMs / 1000.0).toString(), true) { v ->
                v.toDoubleOrNull()?.let { cfg.intervalMs = (it * 1000).toLong() }; saveCfg()
            }
        }
        startValue = addRow(play, "开始延时") {
            Ui.numberDialog(this, p, "开始延时（秒）", (cfg.startDelayMs / 1000.0).toString(), true) { v ->
                v.toDoubleOrNull()?.let { cfg.startDelayMs = (it * 1000).toLong() }; saveCfg()
            }
        }
        limitValue = addRow(play, "自动停止") {
            Ui.numberDialog(this, p, "几分钟后自动停止", cfg.maxMinutes.toString(), false, "0 = 关") { v ->
                v.toIntOrNull()?.let { cfg.maxMinutes = it }; saveCfg()
            }
        }
        jitterPxValue = addRow(play, "随机化位置") {
            Ui.numberDialog(this, p, "随机化位置（± 像素）", trim(cfg.jitterPx), true, "0 = 关") { v ->
                v.toFloatOrNull()?.let { cfg.jitterPx = it }; saveCfg()
            }
        }
        jitterPctValue = addRow(play, "随机化时间") {
            Ui.numberDialog(this, p, "随机化时间（± %）", trim(cfg.jitterPct), true, "0 = 关") { v ->
                v.toFloatOrNull()?.let { cfg.jitterPct = it }; saveCfg()
            }
        }
        val (alertRow, _) = k.switchRow("结束时提醒", storage.alertDone) { on -> storage.alertDone = on }
        play.addView(alertRow)
        play.addView(k.divider())
        val (dimRow, _) = k.switchRow("播放时屏幕变暗", storage.dimScreen) { on ->
            storage.dimScreen = on
            refreshSettings()
        }
        play.addView(dimRow)
        k.valueRow("变暗级别") {
            Ui.numberDialog(this, p, "变暗级别（%）", storage.dimLevel.toString(), false, "10–95") { v ->
                v.toIntOrNull()?.let { storage.dimLevel = it }; refreshSettings()
            }
        }.let { (row, value) ->
            dimLevelRow = row
            dimLevelValue = value
            play.addView(k.divider().also { div -> dimDivider = div })
            play.addView(row)
        }
        root.addView(play)

        // ---- App ----
        root.addView(k.sectionHeader("应用"))
        val app = k.group()
        addRow(app, "教程", "如何录制、播放和编辑", last = true) {
            startActivity(Intent(this, TutorialActivity::class.java))
        }
        root.addView(app)

        // ---- Support ----
        val support = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(4f), k.dp(16f), k.dp(4f), k.dp(4f))
        }
        support.addView(k.text("支持", 12f, 0, p.muted).apply {
            setPadding(0, 0, k.dp(6f), 0)
        })
        fun donate(label: String, url: String, last: Boolean = false) {
            support.addView(k.textButton(label, p.play) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            })
            if (!last) support.addView(k.text("·", 12f, 0, p.muted).apply {
                setPadding(k.dp(2f), 0, k.dp(2f), 0)
            })
        }
        donate("Ko-fi", "https://ko-fi.com/nnazuna")
        donate("Saweria", "https://saweria.co/nnazuna")
        donate("Trakteer", "https://trakteer.id/dedhanof_afp", last = true)
        root.addView(support)


        // First launch: walk through the switches before anything else (the tutorial follows, see onResume).
        if (savedInstanceState == null && !storage.setupSeen && RecorderService.instance == null) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.bg)
        }
        outer.addView(header)
        outer.addView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            isVerticalScrollBarEnabled = false
            addView(root)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        setContentView(outer)
    }

    // ---------- komponen ----------

    private class StatusRow(val view: View, val dot: View, val sub: TextView, val action: TextView)

    private fun statusRow(title: String): StatusRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = k.dp(64f)
            setPadding(k.dp(16f), k.dp(12f), k.dp(12f), k.dp(12f))
        }
        val dot = View(this).apply { background = k.rounded(p.line, 5f) }
        row.addView(dot, LinearLayout.LayoutParams(k.dp(10f), k.dp(10f)).apply { rightMargin = k.dp(14f) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(k.text(title, 16f, 1))
        val sub = k.text("", 13.5f, 0, p.muted).apply { setPadding(0, k.dp(3f), 0, 0) }
        col.addView(sub)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val action = k.textButton("", p.play) {}
        row.addView(action)
        return StatusRow(row, dot, sub, action)
    }

    private fun setDot(v: View, color: Int) {
        v.background = k.rounded(color, 5f)
    }

    private fun stepButton(label: String, onClick: () -> Unit) = k.text(label, 22f, 1, p.ink).apply {
        gravity = Gravity.CENTER
        minWidth = k.dp(44f)
        minHeight = k.dp(44f)
        background = k.ripple(k.rounded(p.bg, 22f, p.line), k.dp(22f).toFloat())
        setOnClickListener { onClick() }
    }

    private fun addRow(group: LinearLayout, label: String, sub: String? = null, last: Boolean = false, onClick: () -> Unit): TextView {
        val (row, value) = k.valueRow(label, sub, onClick)
        group.addView(row)
        if (!last) group.addView(k.divider())
        return value
    }

    /** Four primary actions in a compact dock, not a second card stack. */
    private fun quickActions(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        wrap.addView(k.sectionHeader("工具"))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(quickAction("自动点击", Icon.TAP) { newAutoClicker() }, weighted())
        row.addView(quickAction("截图", Icon.IMAGE) {
            SnapPicker.show(this, k, storage, "截图", null)
        }, weighted())
        row.addView(quickAction("日志", Icon.CLOCK) {
            startActivity(Intent(this, RunLogActivity::class.java))
        }, weighted())
        row.addView(quickAction("设置", Icon.SETTINGS) {
            startActivity(Intent(this, SetupActivity::class.java))
        }, weighted())
        wrap.addView(row)
        wrap.addView(quickRule(horizontal = true))
        return wrap
    }

    private fun weighted() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun quickAction(label: String, icon: Icon, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = k.dp(66f)
            contentDescription = label
            isFocusable = true
            background = k.ripple(null, k.dp(4f).toFloat())
            setOnClickListener { onClick() }
            addView(k.icon(icon, p.play, 21f).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(k.text(label.uppercase(java.util.Locale.US), 10f, 2, p.muted).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, k.dp(6f), 0, 0)
            })
        }

    private fun quickRule(horizontal: Boolean = false): View = View(this).apply {
        setBackgroundColor(p.line)
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        } else {
            LinearLayout.LayoutParams(1, k.dp(44f))
        }
    }

    /** Simple auto clicker: one tap or swipe repeated until stopped. */
    private fun newAutoClicker() {
        TBuilder(this, Ui.dialogTheme(p))
            .setTitle("自动点击器")
            .setItems(arrayOf("点击单点", "滑动", "多目标", "保存的设置")) { _, which ->
                if (which == 2) {
                    val svc = RecorderService.instance
                    if (svc == null) toast("请先开启无障碍服务")
                    else svc.startMultiTarget()
                    return@setItems
                }
                if (which == 3) {
                    val svc = RecorderService.instance
                    if (svc == null) toast("请先开启无障碍服务")
                    else svc.showSavedSetups()
                    return@setItems
                }
                startActivity(
                    Intent(this, PointActivity::class.java)
                        .putExtra(PointActivity.EXTRA_AUTO, true)
                        .putExtra(PointActivity.EXTRA_MODE, if (which == 0) PointActivity.MODE_TAP else PointActivity.MODE_SWIPE)
                )
            }
            .show()
    }

    private fun trim(f: Float): String = if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()

    // ---------- siklus hidup ----------

    override fun onResume() {
        super.onResume()
        RecorderService.onChanged = { runOnUiThread { refresh() } }
        ShizukuBridge.onChanged = { runOnUiThread { refresh() } }
        refresh()
        // Show the short tutorial once, after the setup guide.
        if ((storage.setupSeen || RecorderService.instance != null) && !storage.tutorialSeen) {
            storage.tutorialSeen = true
            startActivity(Intent(this, TutorialActivity::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        RecorderService.onChanged = null
        ShizukuBridge.onChanged = null
    }

    private fun refresh() {
        // Aksesibilitas
        val svc = RecorderService.instance
        if (svc != null) {
            setDot(a11yDot, p.play)
            a11ySub.text = L.t("开")
            a11yAction.visibility = View.GONE
        } else {
            setDot(a11yDot, p.muted)
            a11ySub.text = L.t("关")
            a11yAction.visibility = View.VISIBLE
        }
        panelButton.text = L.t(when {
            svc == null -> "开启无障碍服务"
            svc.isPanelShown -> "隐藏面板"
            else -> "显示面板"
        })
        panelButton.background = k.ripple(k.rounded(if (svc?.isPanelShown == true) p.muted else p.ink, 0f), 0f)

        // Shizuku
        suppressSwitch = true
        shzSwitch.isChecked = storage.useShizuku
        suppressSwitch = false
        val running = ShizukuBridge.isRunning()
        val perm = ShizukuBridge.hasPermission()
        val connected = ShizukuBridge.isConnected
        val (color, msg) = when {
            !storage.useShizuku -> Pair(p.muted, "关 · 可选，播放更快")
            !running -> Pair(p.warn, "Shizuku 未运行")
            !perm -> Pair(p.warn, "需要权限")
            connected -> Pair(p.play, "已连接")
            ShizukuBridge.connecting -> Pair(p.warn, "连接中…")
            else -> Pair(p.rec, "连接失败")
        }
        setDot(shzDot, color)
        shzSub.text = L.t(msg)
        shzAction.visibility = if (storage.useShizuku && running && (!perm || (!connected && !ShizukuBridge.connecting))) View.VISIBLE else View.GONE
        shzAction.text = L.t(if (!perm) "允许" else "重试")
        val diag = if (storage.useShizuku) ShizukuBridge.diagnostics() else ""
        shzDiag.text = diag
        shzDiag.visibility = if (diag.isEmpty()) View.GONE else View.VISIBLE

        refreshSettings()
        renderList()
    }

    private fun loadCfg() {
        val sel = storage.selected()
        cfgRec = sel?.takeIf { it.settings != null }
        cfg = cfgRec?.settings ?: storage.globalSettings()
        suppressScope = true
        scopeSwitch.isChecked = cfgRec != null
        scopeSwitch.isEnabled = sel != null
        scopeSwitch.alpha = if (sel != null) 1f else 0.4f
        suppressScope = false
        scopeTitle.text = L.t("仅用于此录制")
        scopeSub.text = when {
            sel == null -> L.t("请先录制。下方设置对所有录制生效。")
            cfgRec != null -> L.t("仅由 “{}” 使用", sel.name)
            else -> L.t("关：这些设置被所有录制共用")
        }
    }

    /** Save the shown options where they belong, then show them again. */
    private fun saveCfg() {
        val r = cfgRec
        if (r != null) storage.save(r) else storage.saveGlobal(cfg)
        refreshSettings()
    }

    /** Give the selected recording its own copy of the options, or go back to the global ones. */
    private fun setOwnSettings(on: Boolean) {
        val sel = storage.selected() ?: return
        sel.settings = if (on) storage.globalSettings() else null
        storage.save(sel)
        refreshSettings()
        renderList()
    }

    private fun refreshSettings() {
        loadCfg()
        loopsValue.text = if (cfg.loops == 0) "∞" else cfg.loops.toString()
        loopMinus.isEnabled = cfg.loops > 0
        loopMinus.alpha = if (cfg.loops > 0) 1f else 0.35f
        intervalValue.text = Ui.fmtSec(cfg.intervalMs)
        startValue.text = Ui.fmtSec(cfg.startDelayMs)
        limitValue.text = if (cfg.maxMinutes == 0) L.t("关") else L.t("{} min", cfg.maxMinutes)
        jitterPxValue.text = if (cfg.jitterPx == 0f) L.t("关") else "± ${trim(cfg.jitterPx)} 像素"
        dimLevelValue.text = "${storage.dimLevel}%"
        val dimVis = if (storage.dimScreen) View.VISIBLE else View.GONE
        dimLevelRow.visibility = dimVis
        dimDivider?.visibility = dimVis
        jitterPctValue.text = if (cfg.jitterPct == 0f) L.t("关") else "± ${trim(cfg.jitterPct)} %"

        speedBox.removeAllViews()
        for (sp in SPEEDS) {
            val selected = kotlin.math.abs(cfg.speed - sp) < 0.01f
            val label = (if (sp == sp.toInt().toFloat()) sp.toInt().toString() else sp.toString().replace('.', ',')) + "×"
            val t = k.text(label, 15f, if (selected) 2 else 1, if (selected) p.onAccent else p.ink).apply {
                gravity = Gravity.CENTER
                setPadding(0, k.dp(9f), 0, k.dp(9f))
                background = if (selected) k.rounded(p.ink.let { if (p.dark) p.play else it }, 8f) else k.ripple(null, k.dp(8f).toFloat())
                setOnClickListener { cfg.speed = sp; saveCfg() }
            }
            speedBox.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun renderList() {
        val recs = storage.list()
        val key = buildString {
            append(storage.selectedId).append('|')
            recs.forEach {
                append(it.id).append(it.name).append(it.steps().size).append(it.length)
                append(it.images.size).append(it.flows.size).append(it.settings != null).append('|')
            }
        }
        if (key == listKey && listGroup.childCount > 0) return
        listKey = key
        listGroup.removeAllViews()
        if (recs.isEmpty()) {
            listGroup.addView(k.text("暂无录制", 16f, 1).apply {
                setPadding(k.dp(16f), k.dp(18f), k.dp(16f), 0)
            })
            listGroup.addView(k.text(
                "开启服务，点击显示面板，然后按悬浮面板上的红色录制按钮。",
                14f, 0, p.muted,
            ).apply { setPadding(k.dp(16f), k.dp(6f), k.dp(16f), k.dp(18f)) })
            return
        }
        val selected = storage.selected()?.id
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.US)
        recs.forEachIndexed { i, r ->
            val isSel = r.id == selected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = k.dp(64f)
                background = k.ripple(null)
                setOnClickListener { storage.selectedId = r.id; refreshSettings(); renderList() }
                setOnLongClickListener { menu(it, r); true }
            }
            // Penanda rekaman terpilih: garis merah di kiri.
            row.addView(View(this).apply {
                setBackgroundColor(if (isSel) p.rec else 0)
            }, LinearLayout.LayoutParams(k.dp(3f), k.dp(40f)).apply { rightMargin = k.dp(13f) })
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, k.dp(11f), 0, k.dp(11f))
            }
            col.addView(k.text(r.name, 16f, if (isSel) 2 else 1))
            val loops = r.flows.any { it.kind == FlowStep.GOTO }
            col.addView(k.text(
                "${r.steps().size} 个步骤" + (if (r.images.isNotEmpty()) " · ${r.images.size} 个图像" else "") +
                    (if (loops) " · 重复" else "") + (if (r.settings != null) " · 独立设置" else "") + "  ·  ${Ui.fmtSec(r.length)}  ·  ${fmt.format(Date(r.created))}",
                13f, 0, p.muted,
            ).apply { setPadding(0, k.dp(3f), 0, 0); fontFeatureSettings = "tnum" })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(k.textButton("编辑", p.play) { openEditor(r) }.apply {
                setPadding(k.dp(12f), k.dp(12f), k.dp(4f), k.dp(12f))
            })
            row.addView(k.icon(Icon.MORE, p.muted, 22f).apply {
                minimumWidth = k.dp(48f); minimumHeight = k.dp(48f)
                background = k.ripple(null, k.dp(24f).toFloat())
                setOnClickListener { menu(it, r) }
            })
            listGroup.addView(row)
            if (i < recs.size - 1) listGroup.addView(k.divider(16f))
        }
    }

    private fun menu(anchor: View, r: Recording) {
        PopupMenu(this, anchor, Gravity.END).apply {
            menu.add(0, 1, 0, L.t("选择"))
            menu.add(0, 2, 1, L.t("编辑"))
            menu.add(0, 3, 2, L.t("重命名"))
            menu.add(0, 6, 3, L.t("复制"))
            menu.add(0, 4, 3, L.t("导出"))
            menu.add(0, 5, 4, L.t("删除"))
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { storage.selectedId = r.id; refreshSettings(); renderList() }
                    2 -> openEditor(r)
                    6 -> duplicate(r)
                    3 -> rename(r)
                    4 -> export(r)
                    5 -> confirmDelete(r)
                }
                true
            }
            show()
        }
    }

    private fun openEditor(r: Recording) =
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_ID, r.id))

    /** Save a copy, to try changes without touching the original. */
    private fun duplicate(r: Recording) {
        val c = Recording.fromJson(r.toJson())
        val copy = Recording(UUID.randomUUID().toString(), "${r.name} (copy)", System.currentTimeMillis(), c.gestures, c.images, c.flows)
        storage.save(copy)
        storage.selectedId = copy.id
        renderList()
        toast("已复制：${copy.name}")
    }

    private fun rename(r: Recording) {
        val input = android.widget.EditText(this).apply {
            setText(r.name); setSelection(r.name.length); typeface = Ui.font(this@MainActivity, 1)
        }
        val box = k.frame(input, 22f, 4f)
        TBuilder(this, Ui.dialogTheme(p))
            .setTitle("重命名")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { r.name = n; storage.save(r); renderList() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(r: Recording) {
        TBuilder(this, Ui.dialogTheme(p))
            .setTitle("删除 "${r.name}"？")
            .setPositiveButton("删除") { _, _ -> storage.delete(r.id); renderList() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- ekspor / impor ----------

    private fun export(r: Recording) {
        exportId = r.id
        val safe = r.name.replace(Regex("[^A-Za-z0-9 _-]"), "_")
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "$safe.json"),
            REQ_EXPORT,
        )
    }

    @Deprecated("Activity API lama, cukup untuk aplikasi tanpa AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            when (requestCode) {
                REQ_EXPORT -> {
                    val r = storage.get(exportId) ?: return
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(r.toJson().toString(2).toByteArray()) }
                    toast("已导出：${r.name}")
                }
                REQ_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                    val r = Recording.fromJson(JSONObject(text))
                    if (r.steps().isEmpty()) { toast("此文件无操作"); return }
                    val copy = Recording(UUID.randomUUID().toString(), r.name, System.currentTimeMillis(), r.gestures, r.images, r.flows)
                    storage.save(copy)
                    storage.selectedId = copy.id
                    toast("已导入：${copy.name}")
                    renderList()
                }
            }
        } catch (e: Exception) {
            toast("无法读取文件：${e.message}")
        }
    }

    private fun toast(s: String) = LToast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
