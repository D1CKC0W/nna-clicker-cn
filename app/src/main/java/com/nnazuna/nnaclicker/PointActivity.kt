package com.nnazuna.nnaclicker

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.hypot

/** Add or edit a single-finger tap or swipe by placing points on a screenshot. */
class PointActivity : Activity() {

    companion object {
        const val EXTRA_REC = "rec"
        const val EXTRA_MODE = "mode"
        /** Insert after the step that ends at this time (ms); -1 = at the end. */
        const val EXTRA_AFTER = "after"
        /** Start time of that step, so a step starting right when it ends still moves. */
        const val EXTRA_AFTER_START = "afterStart"
        /** Index in [Recording.gestures] to edit. */
        const val EXTRA_GESTURE = "gesture"
        /** Create a new auto clicker recording: the tap or swipe repeats until stopped. */
        const val EXTRA_AUTO = "auto"
        const val MODE_TAP = "tap"
        const val MODE_SWIPE = "swipe"
        const val GAP = 500L
    }

    private lateinit var storage: Storage
    private lateinit var k: Kit
    private val p get() = k.p
    private lateinit var rec: Recording
    private var editing: RecGesture? = null
    private var swipe = false
    private val points = mutableListOf<PointF>()
    /** Hold time for a tap, or duration of a swipe (ms). */
    private var duration = 60L
    private var snapName: String? = null
    private var shot: Bitmap? = null
    private var view: PointView? = null
    private lateinit var coordRows: LinearLayout
    private var auto = false
    /** Auto clicker: pause after each tap or swipe (ms). */
    private var everyMs = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = Storage(this)
        k = Kit(this)
        Ui.styleWindow(this, p)
        auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        rec = if (auto) Recording(java.util.UUID.randomUUID().toString(), "", System.currentTimeMillis(), mutableListOf())
        else storage.get(intent.getStringExtra(EXTRA_REC)) ?: run { finish(); return }

        val gi = intent.getIntExtra(EXTRA_GESTURE, -1)
        editing = rec.gestures.getOrNull(gi)
        val metrics = resources.displayMetrics
        val e = editing
        if (e != null) {
            val pts = e.strokes[0].points
            swipe = e.kind == "滑动"
            points.add(PointF(pts.first().x, pts.first().y))
            if (swipe) points.add(PointF(pts.last().x, pts.last().y))
            duration = e.duration
        } else {
            swipe = intent.getStringExtra(EXTRA_MODE) == MODE_SWIPE
            duration = if (swipe) 300 else 60
        }

        snapName = storage.snaps().firstOrNull()?.name
        loadShot()
        if (points.isEmpty()) {
            val w = shot?.width ?: maxOf(metrics.widthPixels, 1)
            val h = shot?.height ?: maxOf(metrics.heightPixels, 1)
            if (swipe) {
                points.add(PointF(w * 0.3f, h * 0.5f)); points.add(PointF(w * 0.7f, h * 0.5f))
            } else points.add(PointF(w / 2f, h / 2f))
        }
        build()
    }

    override fun onDestroy() {
        super.onDestroy()
        shot?.recycle()
    }

    private fun loadShot() {
        shot?.recycle()
        shot = storage.snapFile(snapName)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(12f), k.dp(16f), k.dp(12f), k.dp(40f))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(k.icon(Icon.BACK, p.ink, 26f).apply {
            minimumWidth = k.dp(44f); minimumHeight = k.dp(44f)
            background = k.ripple(null, k.dp(22f).toFloat())
            setOnClickListener { finish() }
        })
        val title = when {
            auto && swipe -> "自动滑动"
            auto -> "自动点击"
            editing != null && swipe -> "编辑滑动"
            editing != null -> "编辑点击"
            swipe -> "添加滑动"
            else -> "添加点击"
        }
        top.addView(k.text(title, 22f, 2).apply { setPadding(k.dp(6f), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(k.textButton("保存", p.play) { save() })
        root.addView(top)

        val bmp = shot
        if (bmp != null) {
            root.addView(k.text(
                if (swipe) "拖动起点和终点，或点击移动最近的。双指缩放或双击放大。"
                else "拖动圆圈，或点击目标位置。双指缩放或双击放大。",
                14f, 0, p.muted,
            ).apply { setPadding(k.dp(8f), k.dp(4f), k.dp(8f), k.dp(8f)) })
            val maxH = resources.displayMetrics.heightPixels * 55 / 100
            val pv = PointView(this, bmp, points, p.play, maxH)
            pv.onChange = { renderCoords() }
            view = pv
            root.addView(pv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = k.dp(6f)
            })
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(k.dp(4f), 0, k.dp(4f), 0)
            }
            fun zbtn(label: String, onClick: () -> Unit) = k.text(label, 18f, 2, p.ink).apply {
                gravity = Gravity.CENTER
                minWidth = k.dp(48f); minHeight = k.dp(44f)
                background = k.ripple(k.rounded(p.surface, 12f), k.dp(12f).toFloat())
                setOnClickListener { onClick() }
            }
            bar.addView(zbtn("−") { pv.zoomBy(1 / 1.6f) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = k.dp(6f) })
            bar.addView(zbtn("+") { pv.zoomBy(1.6f) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = k.dp(6f) })
            bar.addView(k.textButton("放大", p.play) { pv.zoomToPoints() })
            bar.addView(k.textButton("适应", p.play) { pv.resetZoom() })
            root.addView(bar)
        } else {
            root.addView(k.text(
                "暂无截图。在游戏里按悬浮面板上的相机按钮，或在下方输入位置。",
                14.5f, 0, p.muted,
            ).apply { setPadding(k.dp(8f), k.dp(8f), k.dp(8f), k.dp(8f)) })
        }
        root.addView(k.textButton("使用其他截图", p.play) {
            SnapPicker.show(this, k, storage, "使用截图") { f ->
                snapName = f.name
                loadShot()
                recreateContent()
            }
        }.apply { setPadding(k.dp(8f), k.dp(10f), k.dp(8f), k.dp(10f)) })

        root.addView(k.sectionHeader(if (swipe) "滑动" else "点击"))
        val g = k.group()
        coordRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        g.addView(coordRows)
        root.addView(g)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            addView(root)
        })
        renderCoords()
    }

    private fun recreateContent() = build()

    private fun renderCoords() {
        coordRows.removeAllViews()
        val labels = if (swipe) listOf("开始", "终点") else listOf("位置")
        points.forEachIndexed { i, pt ->
            if (i > 0) coordRows.addView(k.divider())
            val (row, v) = k.valueRow(labels[i]) { editPoint(i) }
            v.text = "${pt.x.toInt()}, ${pt.y.toInt()}"
            coordRows.addView(row)
        }
        coordRows.addView(k.divider())
        val (row, v) = k.valueRow(if (swipe) "时长" else "按住", if (swipe) null else "超过 250 毫秒变为按住") {
            Ui.numberDialog(this, p, if (swipe) "时长（毫秒）" else "按住（毫秒）", duration.toString(), false) { s ->
                s.toLongOrNull()?.let { duration = it.coerceIn(if (swipe) 50L else 20L, 20_000L); renderCoords() }
            }
        }
        v.text = "$duration ms"
        coordRows.addView(row)
        if (swipe) {
            coordRows.addView(k.divider())
            val dist = hypot(points[1].x - points[0].x, points[1].y - points[0].y).toInt()
            val (r2, v2) = k.valueRow("距离")
            v2.text = "$dist px"
            coordRows.addView(r2)
        }
        if (auto) {
            coordRows.addView(k.divider())
            val (r3, v3) = k.valueRow("重复间隔", "Pause after each ${if (swipe) "swipe" else "tap"}. Runs until you stop it.") {
                Ui.numberDialog(this, p, "重复间隔（毫秒）", everyMs.toString(), false, "1000 ms = 1 second") { s ->
                    s.toLongOrNull()?.let { everyMs = it.coerceIn(10L, 3_600_000L); renderCoords() }
                }
            }
            v3.text = "$everyMs 毫秒"
            coordRows.addView(r3)
        }
    }

    private fun editPoint(i: Int) {
        val pt = points[i]
        Ui.numberDialog(this, p, "X", pt.x.toInt().toString(), false) { xs ->
            val x = xs.toFloatOrNull() ?: return@numberDialog
            Ui.numberDialog(this, p, "Y", pt.y.toInt().toString(), false) { ys ->
                val y = ys.toFloatOrNull() ?: return@numberDialog
                pt.set(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
                view?.invalidate()
                renderCoords()
            }
        }
    }

    private fun gesture(at: Long): RecGesture {
        if (!swipe) {
            val p0 = points[0]
            return RecGesture(at, listOf(Stroke(listOf(TouchPoint(p0.x, p0.y, 0), TouchPoint(p0.x, p0.y, duration)))))
        }
        val a = points[0]; val b = points[1]
        val n = (duration / 16).coerceIn(2, 600).toInt()
        val pts = (0..n).map { i ->
            val f = i / n.toFloat()
            TouchPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, duration * i / n)
        }
        return RecGesture(at, listOf(Stroke(pts)))
    }

    private fun save() {
        if (swipe && hypot(points[1].x - points[0].x, points[1].y - points[0].y) < 20f) {
            LToast.makeText(this, "将起点和终点移得更远", Toast.LENGTH_SHORT).show()
            return
        }
        if (auto) { saveAuto(); return }
        val steps = rec.steps()
        val e = editing
        if (e != null) {
            val g = gesture(e.at)
            val delta = g.duration - e.duration
            // Keep the delays after this step the same.
            if (delta != 0L) steps.filter { it.at > e.at }.forEach { it.at += delta }
            val idx = rec.gestures.indexOf(e)
            rec.gestures[idx] = g
        } else {
            val after = intent.getLongExtra(EXTRA_AFTER, -1L)
            val end = steps.maxOfOrNull { it.at + it.duration } ?: -GAP
            val at = if (after >= 0) after + GAP else end + GAP
            val g = gesture(at)
            val afterStart = intent.getLongExtra(EXTRA_AFTER_START, after)
            if (after >= 0) steps.filter { it.at > after || (it.at == after && it.at > afterStart) }
                .forEach { it.at += g.duration + GAP }
            rec.gestures.add(g)
        }
        storage.save(rec)
        LToast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Label → tap/swipe → (pause) → go to label, every time. */
    private fun saveAuto() {
        val label = FlowStep(0, FlowStep.LABEL, ref = FlowStep.newId(), name = "重复")
        val g = gesture(1)
        val jump = FlowStep(g.at + g.duration + everyMs, FlowStep.GOTO, ref = label.ref)
        rec.flows.add(label); rec.gestures.add(g); rec.flows.add(jump)
        rec.name = (if (swipe) "自动滑动" else "自动点击") + " · 每 ${FlowUi.sec(everyMs)}"
        storage.save(rec)
        storage.selectedId = rec.id
        LToast.makeText(this, "已保存并选中。打开游戏并在面板上按播放。", Toast.LENGTH_LONG).show()
        finish()
    }
}
