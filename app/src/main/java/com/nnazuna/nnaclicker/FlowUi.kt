package com.nnazuna.nnaclicker

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Dialogs and texts shared by the screens that edit flow steps (labels, jumps, waits). */
object FlowUi {

    /** "250 ms" below a second, otherwise seconds with up to two decimals: "1.5 s", "2 s". */
    fun sec(ms: Long): String = if (ms in 1..999) "$ms 毫秒" else "${secText(ms)} 秒"

    fun parseSec(v: String): Long? = v.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 1000).toLong() }

    /** A name for a new label that isn't used yet: "标签 1", "标签 2", … */
    fun newLabelName(rec: Recording, prefix: String = "标签"): String {
        val used = rec.flows.filter { it.kind == FlowStep.LABEL }.map { it.name }.toSet()
        var n = 1
        while ("$prefix $n" in used) n++
        return "$prefix $n"
    }

    /** Pick a label of [rec]; shows how to add one when there are none. */
    fun pickLabel(a: Activity, p: Pal, rec: Recording, current: String?, title: String, onPick: (String) -> Unit) {
        val steps = rec.steps()
        val labels = rec.labels()
        if (labels.isEmpty()) {
            TBuilder(a, Ui.dialogTheme(p))
                .setTitle("暂无标签")
                .setMessage("在跳转落地处添加一个标签步骤。在编辑器里用添加步骤，然后选择标签。")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = labels.map { "${it.name}  ·  步骤 ${steps.indexOf(it) + 1}" }.toTypedArray()
        TBuilder(a, Ui.dialogTheme(p))
            .setTitle(title)
            .setSingleChoiceItems(names, labels.indexOfFirst { it.ref == current }) { d, which ->
                d.dismiss()
                onPick(labels[which].ref)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Dialog with one text field. */
    fun textDialog(a: Activity, p: Pal, title: String, value: String, onOk: (String) -> Unit) {
        val input = field(a, value, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        TBuilder(a, Ui.dialogTheme(p))
            .setTitle(title)
            .setView(box(a, p, null, listOf(null to input)))
            .setPositiveButton("保存") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Dialog with several labeled number fields; [onOk] gets the values in order. */
    fun numbersDialog(
        a: Activity, p: Pal, title: String, help: String?, fields: List<Pair<String, String>>, decimal: Boolean,
        onOk: (List<String>) -> Unit,
    ) {
        val type = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_NUMBER
        val inputs = fields.map { (label, v) -> label to field(a, v, type) }
        TBuilder(a, Ui.dialogTheme(p))
            .setTitle(title)
            .setView(box(a, p, help, inputs))
            .setPositiveButton("保存") { _, _ -> onOk(inputs.map { it.second.text.toString().trim().replace(',', '.') }) }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Edit a wait step: a random time from [FlowStep.minMs] to [FlowStep.maxMs]. */
    fun editWait(a: Activity, p: Pal, st: FlowStep, onDone: () -> Unit) =
        numbersDialog(
            a, p, "等待（秒）",
            "在两个值之间等待随机时长。两个值相同则为固定等待。",
            listOf("从" to secText(st.minMs), "到" to secText(st.maxMs)), true,
        ) { v ->
            val lo = parseSec(v[0]) ?: return@numbersDialog
            val hi = parseSec(v[1]) ?: lo
            st.minMs = minOf(lo, hi).coerceIn(0, 3_600_000)
            st.maxMs = maxOf(lo, hi).coerceIn(0, 3_600_000)
            onDone()
        }

    private fun secText(ms: Long): String =
        java.math.BigDecimal(ms).movePointLeft(3).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Pick an installed app that has a launcher icon; [onPick] gets (package, name). */
    fun pickApp(a: Activity, p: Pal, onPick: (String, String) -> Unit) {
        val pm = a.packageManager
        val apps = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != a.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) return
        AlertDialog.Builder(a, Ui.dialogTheme(p))
            .setTitle(L.t("打开应用"))
            .setItems(apps.map { it.second }.toTypedArray()) { _, which -> onPick(apps[which].first, apps[which].second) }
            .setNegativeButton(L.t("取消"), null)
            .show()
    }

    /** Pick a system button for a KEY step. */
    fun pickKey(a: Activity, p: Pal, onPick: (String) -> Unit) {
        val keys = listOf(FlowStep.KEY_BACK, FlowStep.KEY_HOME, FlowStep.KEY_RECENTS)
        TBuilder(a, Ui.dialogTheme(p))
            .setTitle("系统按键")
            .setItems(keys.map { FlowStep.keyName(it) }.toTypedArray()) { _, which -> onPick(keys[which]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun field(a: Activity, value: String, type: Int) = EditText(a).apply {
        inputType = type
        setText(value)
        setSelection(text.length)
        typeface = Ui.font(a, 1)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    }

    private fun box(a: Activity, p: Pal, help: String?, inputs: List<Pair<String?, EditText>>) =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 22f), Ui.dp(a, 8f), Ui.dp(a, 22f), 0)
            if (help != null) addView(note(a, p, help))
            inputs.forEach { (label, input) ->
                if (label != null) addView(note(a, p, label).apply { setPadding(0, Ui.dp(a, 10f), 0, 0) })
                addView(input)
            }
        }

    private fun note(a: Activity, p: Pal, s: String) = TextView(a).apply {
        text = L.t(s)
        typeface = Ui.font(a)
        setTextColor(p.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }
}
