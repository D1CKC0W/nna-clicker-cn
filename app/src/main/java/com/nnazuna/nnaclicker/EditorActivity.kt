package com.nnazuna.nnaclicker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

/**
 * Editor rekaman berbentuk garis waktu: langkah-langkah berurutan (gerakan dan langkah gambar),
 * dengan jeda di antaranya yang bisa diketuk untuk diubah.
 */
class EditorActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
    }

    private lateinit var storage: Storage
    private lateinit var rec: Recording
    private lateinit var k: Kit
    private val p get() = k.p
    private lateinit var listBox: LinearLayout
    private lateinit var summary: TextView
    private var steps: List<Step> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = Storage(this)
        k = Kit(this)
        Ui.styleWindow(this, p)
        if (storage.get(intent.getStringExtra(EXTRA_ID)) == null) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(12f), k.dp(16f), k.dp(16f), k.dp(40f))
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
        top.addView(k.text("", 22f, 2).apply {
            tag = "title"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(k.dp(6f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        summary = k.text("", 14f, 0, p.muted).apply {
            setPadding(k.dp(50f), k.dp(2f), 0, k.dp(6f))
            fontFeatureSettings = "tnum"
        }
        root.addView(summary)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(k.dp(38f), 0, 0, k.dp(8f))
        }
        actions.addView(k.textButton("播放", p.play) { playFromStart() }.apply {
            setPadding(k.dp(12f), k.dp(10f), k.dp(12f), k.dp(10f))
        })
        actions.addView(k.textButton("设置全部延时", p.play) { setAllGaps() }.apply {
            setPadding(k.dp(12f), k.dp(10f), k.dp(12f), k.dp(10f))
        })
        actions.addView(k.textButton("帮助", p.play) { showHelp() }.apply {
            setPadding(k.dp(12f), k.dp(10f), k.dp(12f), k.dp(10f))
        })
        root.addView(actions)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(4f), 0, 0, 0)
        }
        root.addView(listBox)

        root.addView(k.pillButton("+  添加步骤", p.play) { addStep(-1) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = k.dp(18f); leftMargin = k.dp(4f) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        // Reload: the image step screen saves the recording on its own.
        rec = storage.get(intent.getStringExtra(EXTRA_ID)) ?: run { finish(); return }
        (window.decorView.findViewWithTag<TextView>("title"))?.text = rec.name
        render()
    }

    private fun gapBefore(i: Int): Long {
        if (i == 0) return 0
        val prev = steps[i - 1]
        return (steps[i].at - (prev.at + prev.duration)).coerceAtLeast(0)
    }

    /** Ubah jeda sebelum langkah [i]; langkah sesudahnya ikut bergeser. */
    private fun setGap(i: Int, newGap: Long) {
        val delta = newGap.coerceAtLeast(0) - gapBefore(i)
        for (n in i until steps.size) steps[n].at += delta
    }

    private fun save() {
        // Keep the on-screen order even when two steps end up at the same time.
        for (i in 1 until steps.size) {
            if (steps[i].at <= steps[i - 1].at) steps[i].at = steps[i - 1].at + 1
        }
        storage.save(rec)
        render()
    }

    /**
     * Move a step to another position. The delays stay where they were in the sequence,
     * so the rhythm of the recording doesn't change.
     */
    private fun move(from: Int, to: Int) {
        if (to !in steps.indices || from == to) return
        val gaps = steps.indices.map { gapBefore(it) }
        val order = steps.toMutableList()
        order.add(to, order.removeAt(from))
        retime(order, gaps)
        save()
    }

    /** Give the steps in [order] new times so that step i starts [gaps]\[i] after the previous one ends. */
    private fun retime(order: List<Step>, gaps: List<Long>) {
        val start = steps.firstOrNull()?.at ?: 0L
        var prevEnd = 0L
        order.forEachIndexed { i, st ->
            st.at = if (i == 0) start else prevEnd + gaps[i]
            prevEnd = st.at + st.duration
        }
        steps = order
    }

    /**
     * Insert the new step [s] at position [index] with [gap] before it. The steps after it keep
     * their own delays, so the rest of the recording keeps its rhythm.
     */
    private fun insertAt(index: Int, s: Step, gap: Long) {
        val gaps = steps.indices.map { gapBefore(it) }.toMutableList()
        val order = steps.toMutableList()
        val at = index.coerceIn(0, order.size)
        order.add(at, s)
        gaps.add(at, gap)
        when (s) {
            is RecGesture -> rec.gestures.add(s)
            is ImageStep -> rec.images.add(s)
            is FlowStep -> rec.flows.add(s)
        }
        retime(order, gaps)
        save()
    }

    private fun render() {
        steps = rec.steps()
        val extra = listOfNotNull(
            rec.images.size.takeIf { it > 0 }?.let { L.t("{} image", it) },
            rec.flows.size.takeIf { it > 0 }?.let { L.t("{} flow", it) },
        )
        summary.text = L.t("{} steps", steps.size) + (if (extra.isNotEmpty()) " (${extra.joinToString(", ")})" else "") +
            "  ·  ${Ui.fmtSec(rec.length)}"
        listBox.removeAllViews()
        val loops = loopRanges()
        steps.forEachIndexed { i, s ->
            if (i > 0) listBox.addView(withStrips(gapView(i), loops.count { i > it.first && i <= it.last }))
            listBox.addView(withStrips(when (s) {
                is ImageStep -> imageView(i, s)
                is FlowStep -> flowView(i, s)
                else -> gestureView(i, s as RecGesture)
            }, loops.count { i in it }, top = i > 0))
        }
    }

    /** Steps from a label to a Go to that jumps back to it: the part that repeats. */
    private fun loopRanges(): List<IntRange> = steps.withIndex().mapNotNull { (i, s) ->
        if (s !is FlowStep || s.kind != FlowStep.GOTO) return@mapNotNull null
        val t = labelIndex(s.ref)
        if (t in 0 until i) t..i else null
    }

    private fun labelIndex(ref: String?): Int =
        steps.indexOfFirst { it is FlowStep && it.kind == FlowStep.LABEL && it.ref == ref }

    /** Draw [depth] colored bars on the left, so the steps that repeat are easy to see. */
    private fun withStrips(v: View, depth: Int, top: Boolean = true): View {
        if (depth <= 0) return v
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(depth) {
            box.addView(View(this).apply { background = k.rounded(p.play, 2f) },
                LinearLayout.LayoutParams(k.dp(4f), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = k.dp(5f) })
        }
        box.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    /** Penghubung antar langkah: garis vertikal + jeda yang bisa diketuk. */
    private fun gapView(i: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = k.dp(40f)
            background = k.ripple(null, k.dp(10f).toFloat())
            setOnClickListener { askGap(i) }
        }
        row.addView(View(this).apply { setBackgroundColor(p.line) },
            LinearLayout.LayoutParams(k.dp(2f), k.dp(40f)).apply { leftMargin = k.dp(17f); rightMargin = k.dp(20f) })
        val gap = gapBefore(i)
        row.addView(k.text(if (gap <= 1) "no wait" else "等待 ${FlowUi.sec(gap)}", 13f, 1, p.muted).apply {
            fontFeatureSettings = "tnum"
            setPadding(k.dp(10f), k.dp(3f), k.dp(10f), k.dp(3f))
            background = k.rounded(p.surface, 12f)
        })
        return row
    }

    private fun number(i: Int) = k.text("${i + 1}", 13f, 2, p.ink).apply {
        gravity = Gravity.CENTER
        fontFeatureSettings = "tnum"
        background = k.rounded(p.bg, 14f)
    }

    private fun moreButton(i: Int) = k.icon(Icon.MORE, p.muted, 22f).apply {
        minimumWidth = k.dp(44f); minimumHeight = k.dp(44f)
        background = k.ripple(null, k.dp(22f).toFloat())
        setOnClickListener { stepMenu(it, i) }
    }

    private fun gestureView(i: Int, g: RecGesture): View {
        val pts = g.strokes[0].points
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = k.rounded(p.surface, 14f)
            setPadding(0, k.dp(10f), k.dp(4f), k.dp(10f))
        }
        row.addView(number(i), LinearLayout.LayoutParams(k.dp(28f), k.dp(28f)).apply { leftMargin = k.dp(4f); rightMargin = k.dp(12f) })
        row.addView(k.icon(if (g.kind == "滑动") Icon.SWIPE else Icon.TAP, p.ink, 22f),
            LinearLayout.LayoutParams(k.dp(28f), k.dp(28f)).apply { rightMargin = k.dp(12f) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(k.text(g.kind, 16f, 2))
        val where = if (g.kind == "滑动") {
            "${pts.first().x.toInt()}, ${pts.first().y.toInt()}  →  ${pts.last().x.toInt()}, ${pts.last().y.toInt()}"
        } else "${pts.first().x.toInt()}, ${pts.first().y.toInt()}"
        col.addView(k.text("at $where  ·  ${g.duration} ms", 13f, 0, p.muted).apply {
            setPadding(0, k.dp(3f), 0, 0)
            fontFeatureSettings = "tnum"
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(moreButton(i))
        return row
    }

    private fun imageView(i: Int, s: ImageStep): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = k.ripple(k.rounded(p.surface, 14f, if (s.ready) null else p.warn), k.dp(14f).toFloat())
            setPadding(0, k.dp(10f), k.dp(4f), k.dp(10f))
            setOnClickListener { openImage(s) }
        }
        row.addView(number(i), LinearLayout.LayoutParams(k.dp(28f), k.dp(28f)).apply { leftMargin = k.dp(4f); rightMargin = k.dp(12f) })

        val thumb = templateBitmap(s)
        if (thumb != null) {
            row.addView(ImageView(this).apply {
                setImageBitmap(thumb)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = k.rounded(p.bg, 8f)
                clipToOutline = true
            }, LinearLayout.LayoutParams(k.dp(52f), k.dp(40f)).apply { rightMargin = k.dp(12f) })
        } else {
            row.addView(k.icon(Icon.IMAGE, p.warn, 22f), LinearLayout.LayoutParams(k.dp(52f), k.dp(40f)).apply { rightMargin = k.dp(12f) })
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(k.text(s.title, 16f, 2))
        val sub = if (!s.ready) "点击选择区域" else
            (if (s.timeoutMs <= 0) "仅检查一次" else "最多等待 ${s.timeoutMs / 1000} 秒") +
                "  ·  ${if (s.anywhere) "anywhere" else "near its spot"}  ·  ${(s.threshold * 100).toInt()}% match"
        col.addView(k.text(sub, 13f, if (s.ready) 0 else 1, if (s.ready) p.muted else p.warn).apply {
            setPadding(0, k.dp(3f), 0, 0)
        })
        branchText(s)?.let { b ->
            col.addView(k.text(b, 13f, 1, p.play).apply { setPadding(0, k.dp(3f), 0, 0) })
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(moreButton(i))
        return row
    }

    /** "找到 → 标签 1 · 未找到 → 停止", or null when the step just continues / stops as usual. */
    private fun branchText(s: ImageStep): String? {
        val yes = if (s.action == ImageStep.ACTION_GONE) "消失" else "找到"
        val no = if (s.action == ImageStep.ACTION_GONE) "仍在" else "未找到"
        val parts = mutableListOf<String>()
        s.foundGoto?.let { parts += "$yes → ${rec.labelName(it) ?: "?"}" }
        if (s.onTimeout == ImageStep.ON_TIMEOUT_GOTO) parts += "$no → ${rec.labelName(s.failGoto) ?: "?"}"
        else if (parts.isNotEmpty() || s.timeoutMs <= 0) parts += "$no → " + if (s.onTimeout == ImageStep.ON_TIMEOUT_SKIP) "next" else "stop"
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }

    private fun flowView(i: Int, s: FlowStep): View {
        val missing = s.kind == FlowStep.GOTO && rec.labelName(s.ref) == null
        val isLabel = s.kind == FlowStep.LABEL
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val base = if (isLabel) k.rounded(p.bg, 14f, p.play) else k.rounded(p.surface, 14f, if (missing) p.warn else null)
            background = k.ripple(base, k.dp(14f).toFloat())
            setPadding(0, k.dp(8f), k.dp(4f), k.dp(8f))
            setOnClickListener { editFlow(s) }
        }
        row.addView(number(i), LinearLayout.LayoutParams(k.dp(28f), k.dp(28f)).apply { leftMargin = k.dp(4f); rightMargin = k.dp(12f) })
        val (icon, color) = when (s.kind) {
            FlowStep.LABEL -> Icon.FLAG to p.play
            FlowStep.GOTO -> Icon.JUMP to (if (missing) p.warn else p.play)
            FlowStep.WAIT -> Icon.CLOCK to p.muted
            FlowStep.KEY -> Icon.BACK to p.ink
            FlowStep.LAUNCH -> Icon.PLAY to p.ink
            else -> Icon.STOP to p.rec
        }
        row.addView(k.icon(icon, color, 22f), LinearLayout.LayoutParams(k.dp(28f), k.dp(28f)).apply { rightMargin = k.dp(12f) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = when (s.kind) {
            FlowStep.LABEL -> s.name.ifEmpty { "标签" }
            FlowStep.GOTO -> if (missing) "跳转到…" else "跳转到 ${rec.labelName(s.ref)}"
            FlowStep.WAIT -> "等待"
            FlowStep.KEY -> "按下 ${FlowStep.keyName(s.ref)}"
            FlowStep.LAUNCH -> "打开 ${s.name.ifEmpty { s.ref }}"
            else -> "停止播放"
        }
        col.addView(k.text(title, 16f, 2, if (isLabel) p.play else p.ink))
        val sub = when (s.kind) {
            FlowStep.LABEL -> {
                val users = steps.withIndex().filter { (_, x) ->
                    (x is FlowStep && x.kind == FlowStep.GOTO && x.ref == s.ref) ||
                        (x is ImageStep && (x.foundGoto == s.ref || (x.onTimeout == ImageStep.ON_TIMEOUT_GOTO && x.failGoto == s.ref)))
                }.map { it.index + 1 }
                if (users.isEmpty()) "标签 · 暂无跳转指向此处" else "Label · step ${users.joinToString(", ")} jump here"
            }
            FlowStep.GOTO -> if (missing) "点击选择标签" else {
                val t = labelIndex(s.ref)
                if (t < i) {
                    val part = if (t + 2 <= i) "步骤 ${t + 2}–$i" else "从步骤 ${t + 1}"
                    if (s.count == 0) "重复 $part 直到停止" else "重复 $part · 共 ${s.count + 1} 次"
                } else {
                    "跳到步骤 ${t + 1}" + if (s.count == 0) "" else " · 前 ${s.count} 次"
                }
            }
            FlowStep.WAIT -> if (s.maxMs > s.minMs) "随机 ${FlowUi.sec(s.minMs)} – ${FlowUi.sec(s.maxMs)}" else FlowUi.sec(s.minMs)
            FlowStep.KEY -> "系统按键"
            FlowStep.LAUNCH -> "打开应用，或将其带到前台"
            else -> "在此结束播放"
        }
        col.addView(k.text(sub, 13f, if (missing) 1 else 0, if (missing) p.warn else p.muted).apply {
            setPadding(0, k.dp(3f), 0, 0)
            fontFeatureSettings = "tnum"
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(moreButton(i))
        return row
    }

    private fun editFlow(s: FlowStep) {
        when (s.kind) {
            FlowStep.LABEL -> FlowUi.textDialog(this, p, "标签名", s.name) { v ->
                if (v.isNotEmpty()) { s.name = v; save() }
            }
            FlowStep.GOTO -> {
                val target = rec.labelName(s.ref)
                TBuilder(this, Ui.dialogTheme(p))
                    .setTitle("跳转到")
                    .setItems(arrayOf(
                        "Label: ${target ?: "none"}",
                        "跳转：" + if (s.count == 0) "每次" else "${s.count}，然后继续",
                    )) { _, which ->
                        if (which == 0) FlowUi.pickLabel(this, p, rec, s.ref, "跳转到标签") { s.ref = it; save() }
                        else askJumps(s) { save() }
                    }
                    .show()
            }
            FlowStep.WAIT -> FlowUi.editWait(this, p, s) { save() }
            FlowStep.KEY -> FlowUi.pickKey(this, p) { s.ref = it; save() }
            FlowStep.LAUNCH -> FlowUi.pickApp(this, p) { pkg, name -> s.ref = pkg; s.name = name; save() }
        }
    }

    private fun askJumps(s: FlowStep, onDone: () -> Unit) =
        Ui.numberDialog(
            this, p, "跳转次数", s.count.toString(), false,
            "0 = 每次都跳转。否则跳转此次后继续下一步。" +
                "要将一段重复 3 次，在其前放一个标签并跳转 2 次。",
        ) { v ->
            v.toIntOrNull()?.let { s.count = it.coerceIn(0, 100_000); onDone() }
        }

    private fun openImage(s: ImageStep) {
        startActivity(
            Intent(this, ImageStepActivity::class.java)
                .putExtra(ImageStepActivity.EXTRA_REC, rec.id)
                .putExtra(ImageStepActivity.EXTRA_INDEX, rec.images.indexOf(s))
        )
    }

    private fun stepMenu(anchor: View, i: Int) {
        PopupMenu(this, anchor, Gravity.END).apply {
            if (steps[i] is ImageStep) menu.add(0, 4, 0, L.t("编辑图像"))
            val g = steps[i] as? RecGesture
            if (g != null && g.strokes.size == 1) menu.add(0, 8, 0, L.t("编辑位置"))
            (steps[i] as? FlowStep)?.let { f ->
                when (f.kind) {
                    FlowStep.LABEL -> menu.add(0, 10, 0, L.t("重命名"))
                    FlowStep.GOTO, FlowStep.WAIT, FlowStep.KEY, FlowStep.LAUNCH -> menu.add(0, 10, 0, L.t("编辑"))
                    else -> {}
                }
            }
            menu.add(0, 9, 7, L.t("在此后添加步骤"))
            menu.add(0, 11, 8, L.t("从此处播放"))
            menu.add(0, 12, 0, L.t("重复步骤…"))
            if (i > 0) menu.add(0, 5, 1, L.t("上移"))
            if (i < steps.size - 1) menu.add(0, 6, 2, L.t("下移"))
            if (steps.size > 2) menu.add(0, 7, 3, L.t("移动到…"))
            if (i > 0) menu.add(0, 1, 4, L.t("编辑延时"))
            menu.add(0, 2, 5, L.t("复制"))
            menu.add(0, 3, 6, L.t("删除"))
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> askGap(i)
                    2 -> duplicate(i)
                    3 -> delete(i)
                    4 -> openImage(steps[i] as ImageStep)
                    8 -> editGesture(steps[i] as RecGesture)
                    10 -> editFlow(steps[i] as FlowStep)
                    11 -> playFrom(i)
                    12 -> repeatSteps(i)
                    9 -> addStep(i)
                    5 -> move(i, i - 1)
                    6 -> move(i, i + 1)
                    7 -> Ui.numberDialog(this@EditorActivity, p, "将步骤 ${i + 1} 移动到", "${i + 1}", false, "1–${steps.size}") { v ->
                        v.toIntOrNull()?.let { n -> move(i, (n - 1).coerceIn(0, steps.size - 1)) }
                    }
                }
                true
            }
            show()
        }
    }

    private fun askGap(i: Int) =
        Ui.numberDialog(this, p, "步骤 ${i + 1} 前的延时（毫秒）", gapBefore(i).toString(), false) { v ->
            v.toLongOrNull()?.let { setGap(i, it); save() }
        }

    private fun setAllGaps() =
        Ui.numberDialog(this, p, "设置全部延时（毫秒）", "500", false) { v ->
            v.toLongOrNull()?.let { ms ->
                for (i in 1 until steps.size) setGap(i, ms)
                save()
            }
        }

    /** Sisipkan salinan langkah [i] tepat setelahnya, dengan jeda yang sama seperti sebelum langkah itu. */
    private fun duplicate(i: Int) {
        val s = steps[i]
        val gap = if (i > 0) gapBefore(i) else 300L
        val copy: Step = when (s) {
            is RecGesture -> RecGesture(s.at, s.strokes)
            is ImageStep -> s.copy()
            is FlowStep -> s.copy().apply {
                if (kind == FlowStep.LABEL) { ref = FlowStep.newId(); name = FlowUi.newLabelName(rec) }
            }
            else -> return
        }
        insertAt(i + 1, copy, gap)
    }

    private fun delete(i: Int) {
        if (steps.size <= 1) {
            TBuilder(this, Ui.dialogTheme(p))
                .setMessage("录制至少需要一个步骤。")
                .setPositiveButton("OK", null).show()
            return
        }
        when (val s = steps[i]) {
            is RecGesture -> rec.gestures.remove(s)
            is ImageStep -> rec.images.remove(s)
            is FlowStep -> {
                if (s.kind == FlowStep.LABEL) {
                    val users = rec.flows.count { it.kind == FlowStep.GOTO && it.ref == s.ref } +
                        rec.images.count { it.foundGoto == s.ref || it.failGoto == s.ref }
                    if (users > 0) {
                        TBuilder(this, Ui.dialogTheme(p))
                            .setTitle("删除 ${s.name}？")
                            .setMessage("$users 个步骤跳转到此标签。图像步骤将改为继续下一步，跳转步骤需要新标签。")
                            .setPositiveButton("删除") { _, _ -> deleteLabel(s) }
                            .setNegativeButton("取消", null)
                            .show()
                        return
                    }
                }
                rec.flows.remove(s)
            }
        }
        save()
    }

    private fun deleteLabel(s: FlowStep) {
        rec.flows.remove(s)
        for (img in rec.images) {
            if (img.foundGoto == s.ref) img.foundGoto = null
            if (img.failGoto == s.ref) {
                img.failGoto = null
                if (img.onTimeout == ImageStep.ON_TIMEOUT_GOTO) img.onTimeout = ImageStep.ON_TIMEOUT_SKIP
            }
        }
        save()
    }

    /** Add a tap, swipe, or image step after step [after] (-1 = at the end). */
    private fun addStep(after: Int) {
        val anchor = steps.getOrNull(after)
        val afterEnd = anchor?.let { it.at + it.duration } ?: -1L
        val afterStart = anchor?.at ?: -1L
        val index = if (after < 0) steps.size else after + 1
        stepSheet(if (anchor == null) "添加步骤" else "在步骤 ${after + 1} 后添加") { which ->
                when (which) {
                    0, 1 -> startActivity(
                        Intent(this, PointActivity::class.java)
                            .putExtra(PointActivity.EXTRA_REC, rec.id)
                            .putExtra(PointActivity.EXTRA_MODE, if (which == 0) PointActivity.MODE_TAP else PointActivity.MODE_SWIPE)
                            .putExtra(PointActivity.EXTRA_AFTER, afterEnd)
                            .putExtra(PointActivity.EXTRA_AFTER_START, afterStart)
                    )
                    2 -> addImageStep(afterEnd, afterStart)
                    3 -> FlowStep(0, FlowStep.WAIT, minMs = 1000, maxMs = 2000).let { st ->
                        FlowUi.editWait(this, p, st) { insertAt(index, st, 0) }
                    }
                    4 -> {
                        val st = FlowStep(0, FlowStep.LABEL, ref = FlowStep.newId(), name = FlowUi.newLabelName(rec))
                        FlowUi.textDialog(this, p, "标签名", st.name) { v ->
                            if (v.isNotEmpty()) st.name = v
                            insertAt(index, st, 0)
                        }
                    }
                    5 -> FlowUi.pickLabel(this, p, rec, null, "跳转到标签") { ref ->
                        val st = FlowStep(0, FlowStep.GOTO, ref = ref)
                        askJumps(st) { insertAt(index, st, 0) }
                    }
                    6 -> FlowUi.pickKey(this, p) { key -> insertAt(index, FlowStep(0, FlowStep.KEY, ref = key), 0) }
                    7 -> FlowUi.pickApp(this, p) { pkg, name ->
                        insertAt(index, FlowStep(0, FlowStep.LAUNCH, ref = pkg, name = name), 0)
                    }
                    8 -> insertAt(index, FlowStep(0, FlowStep.STOP), 0)
                    9 -> repeatSteps(if (after < 0) maxOf(steps.size - 1, 0) else after)
                }
        }
    }

    /** Choices for "添加步骤", grouped and explained, with the ids used by [addStep]. */
    private fun stepSheet(title: String, onPick: (Int) -> Unit) {
        val groups = listOf(
            "触摸" to listOf(
                Triple(0, "点击", "点击单点"),
                Triple(1, "滑动", "从一点拖到另一点"),
            ),
            "图像" to listOf(
                Triple(2, "图像", "等待或点击屏幕上的内容"),
            ),
            "重复与逻辑" to listOfNotNull(
                Triple(9, "重复步骤", "将某些步骤运行多次").takeIf { steps.isNotEmpty() },
                Triple(3, "等待", "暂停固定或随机时长"),
                Triple(4, "标签", "其他步骤可跳转到的位置"),
                Triple(5, "跳转到标签", "跳转到标签"),
                Triple(8, "停止播放", "在此结束播放"),
            ),
            "系统" to listOf(
                Triple(6, "返回 / 主屏 / 最近应用", "按下系统按键"),
                Triple(7, "打开应用", "打开或切换到应用"),
            ),
        )
        val icons = mapOf(0 to Icon.TAP, 1 to Icon.SWIPE, 2 to Icon.IMAGE, 9 to Icon.JUMP, 3 to Icon.CLOCK,
            4 to Icon.FLAG, 5 to Icon.JUMP, 8 to Icon.STOP, 6 to Icon.BACK, 7 to Icon.PLAY)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(8f), k.dp(4f), k.dp(8f), k.dp(8f))
        }
        var dialog: AlertDialog? = null
        for ((header, items) in groups) {
            box.addView(k.text(header.uppercase(), 12f, 2, p.muted).apply {
                letterSpacing = 0.06f
                setPadding(k.dp(12f), k.dp(14f), k.dp(12f), k.dp(4f))
            })
            for ((id, name, help) in items) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = k.dp(56f)
                    setPadding(k.dp(12f), k.dp(8f), k.dp(12f), k.dp(8f))
                    background = k.ripple(null, k.dp(12f).toFloat())
                    setOnClickListener { dialog?.dismiss(); onPick(id) }
                }
                row.addView(k.icon(icons[id] ?: Icon.PLAY, if (id == 8) p.rec else p.play, 22f),
                    LinearLayout.LayoutParams(k.dp(36f), k.dp(36f)).apply { rightMargin = k.dp(12f) })
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                col.addView(k.text(name, 16f, 1))
                col.addView(k.text(help, 13f, 0, p.muted).apply { setPadding(0, k.dp(2f), 0, 0) })
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                box.addView(row)
            }
        }
        dialog = TBuilder(this, Ui.dialogTheme(p))
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * Beginner-friendly loop: pick a range of steps and how many times it runs; this adds the
     * label before the first step and the Go to after the last one.
     */
    private fun repeatSteps(at: Int) {
        if (steps.isEmpty()) return
        val first = at.coerceIn(0, steps.size - 1) + 1
        FlowUi.numbersDialog(
            this, p, "重复步骤",
            "从第一个到最后一个步骤号运行此次数。0 = 直到停止播放。",
            listOf("从步骤" to "$first", "到步骤" to "$first", "次数" to "3"), false,
        ) { v ->
            val a = v[0].toIntOrNull() ?: return@numbersDialog
            val b = v[1].toIntOrNull() ?: a
            val times = v[2].toIntOrNull() ?: return@numbersDialog
            val from = (minOf(a, b) - 1).coerceIn(0, steps.size - 1)
            val to = (maxOf(a, b) - 1).coerceIn(0, steps.size - 1)
            if (times == 1) return@numbersDialog
            val label = FlowStep(0, FlowStep.LABEL, ref = FlowStep.newId(), name = FlowUi.newLabelName(rec, "重复"))
            val jump = FlowStep(0, FlowStep.GOTO, ref = label.ref, count = if (times <= 0) 0 else times - 1)
            // Add the jump first so the step numbers for the label stay the same.
            insertAt(to + 1, jump, 0)
            insertAt(from, label, 0)
            LToast.makeText(this,
                if (times <= 0) "步骤 ${from + 1}–${to + 1} 重复直到停止" else "步骤 ${from + 1}–${to + 1} 运行 $times 次",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun playFromStart() {
        val svc = RecorderService.instance
        if (svc == null) {
            LToast.makeText(this, "请先开启无障碍服务", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        LToast.makeText(this, "打开游戏。3 秒后开始播放…", android.widget.Toast.LENGTH_LONG).show()
        svc.playFrom(rec.id, -1)
        moveTaskToBack(true)
    }

    private fun showHelp() {
        TBuilder(this, Ui.dialogTheme(p))
            .setTitle("步骤如何运作")
            .setMessage(
                "步骤从上到下运行。点击步骤之间的等待可更改延时。\n\n" +
                    "重复步骤：在某个步骤上打开 ⋮ 并选择重复步骤… 以将一段运行多次。\n\n" +
                    "标签 + 跳转：标签标记一个位置，跳转跳到该处。往回跳转会重复中间的步骤；" +
                    "左侧的彩色条表示哪些步骤在重复。\n\n" +
                    "图像步骤可选择下一步执行什么：在如果找到和如果未找到下，选择下一步、一个标签或停止。\n\n" +
                    "音量下键可随时停止播放。"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /** Switch to the game and play from step [i] after a short countdown. */
    private fun playFrom(i: Int) {
        val svc = RecorderService.instance
        if (svc == null) {
            LToast.makeText(this, "请先开启无障碍服务", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        LToast.makeText(this, "打开游戏。3 秒后从步骤 ${i + 1} 开始播放…", android.widget.Toast.LENGTH_LONG).show()
        svc.playFrom(rec.id, steps[i].at)
        moveTaskToBack(true)
    }

    private fun editGesture(g: RecGesture) {
        startActivity(
            Intent(this, PointActivity::class.java)
                .putExtra(PointActivity.EXTRA_REC, rec.id)
                .putExtra(PointActivity.EXTRA_GESTURE, rec.gestures.indexOf(g))
        )
    }

    /** Add an image step from a saved screenshot, then open it to choose the area. */
    private fun addImageStep(afterEnd: Long, afterStart: Long) {
        SnapPicker.show(this, k, storage, "从此添加图像步骤") { file ->
            val gap = PointActivity.GAP
            val at = if (afterEnd >= 0) {
                steps.filter { it.at > afterEnd || (it.at == afterEnd && it.at > afterStart) }.forEach { it.at += gap }
                afterEnd + gap
            } else (steps.maxOfOrNull { it.at + it.duration } ?: -gap) + gap
            val s = ImageStep(at, file.name)
            rec.images.add(s)
            storage.save(rec)
            openImage(s)
        }
    }
}
