package com.nnazuna.nnaclicker

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast

/**
 * UI localization. [t] looks up a Chinese translation table and fills the `{}`
 * holes used by templates, falling back to the original English text when no
 * entry exists, so untranslated strings stay readable instead of breaking.
 */
object L {
    private val zh: Map<String, String> = mapOf(
        "Add step after" to "在此后添加步骤",
        "All entries and their screenshots will be deleted." to "所有记录及其截图将被删除。",
        "Cancel" to "取消",
        "Check once" to "仅检查一次",
        "Clear" to "清除",
        "Clear the run log?" to "清除运行日志？",
        "Close" to "关闭",
        "Delete" to "删除",
        "Delete this entry?" to "删除此记录？",
        "Duplicate" to "复制",
        "Edit" to "编辑",
        "Edit delay" to "编辑延时",
        "Edit image" to "编辑图像",
        "Edit position" to "编辑位置",
        "Export" to "导出",
        "Fix step" to "修复步骤",
        "Fix step {}" to "修复步骤 {}",
        "Loop {}" to "循环 {}",
        "Match set to {}%" to "匹配度已设为 {}%",
        "Move down" to "下移",
        "Move to…" to "移动到…",
        "Move up" to "上移",
        "Nothing similar was on the screen. The game may have shown a different screen, a popup, or loaded slowly." to "屏幕上没有相似内容。可能是显示了不同画面、弹出了窗口，或加载较慢。",
        "Off" to "关",
        "Off: these settings are used by all recordings" to "关：这些设置被所有录制共用",
        "On" to "开",
        "Only for this recording" to "仅用于此录制",
        "Open app" to "打开应用",
        "Open the step" to "打开步骤",
        "Paused" to "已暂停",
        "Play from here" to "从此处播放",
        "Record something first. Settings below apply to all." to "请先录制。下方设置对所有录制生效。",
        "Recording {}" to "录制 {}",
        "Rename" to "重命名",
        "Repeat steps…" to "重复步骤…",
        "Search anywhere on the screen" to "在整个屏幕搜索",
        "Select" to "选择",
        "Set match to {}%" to "设置匹配度为 {}%",
        "Start" to "开始",
        "Starting…" to "启动中…",
        "Step {} · closest {}%, needs {}%" to "步骤 {} · 最接近 {}%，需要 {}%",
        "Step {} · image not found" to "步骤 {} · 未找到图像",
        "Tap a spot" to "点击一个位置",
        "Tap the spot to repeat" to "点击要重复的位置",
        "Target {}/{}" to "目标 {}/{}",
        "The closest match was {}%, but {}% is needed. If the right thing is on screen, lower the match or use this screenshot." to "最接近的匹配为 {}%，但需要 {}%。若正确内容在屏幕上，请降低匹配度或使用此截图。",
        "The step now searches the whole screen" to "此步骤现在搜索整个屏幕",
        "The step now waits up to {} s" to "此步骤现在最多等待 {} 秒",
        "This step has been changed or deleted since then." to "此步骤此后已被更改或删除。",
        "Use this screenshot for the step" to "将此截图用于该步骤",
        "Used only by “{}”" to "仅由 “{}” 使用",
        "Wait longer ({} s)" to "等待更久（{} 秒）",
        "{} flow" to "{} 个流程",
        "{} image" to "{} 个图像",
        "{} min" to "{} 分钟",
        "{} steps" to "{} 个步骤",
        "{} targets" to "{} 个目标",
        "{} · step {}" to "{} · 步骤 {}",
    )

    fun load(@Suppress("UNUSED_PARAMETER") c: Context) = Unit

    fun t(s: CharSequence?): String {
        val src = s?.toString() ?: return ""
        return zh[src] ?: src
    }

    fun t(template: String, vararg args: Any?): String {
        val tpl = zh[template] ?: template
        if (args.isEmpty()) return tpl
        return fill(tpl, args.map { it?.toString() ?: "" })
    }

    /** Replace `{}` holes in order; `{1}`, `{2}` … pick an argument by position. */
    private fun fill(tpl: String, args: List<String>): String {
        if (!tpl.contains('{')) return tpl
        val sb = StringBuilder()
        var next = 0
        var i = 0
        while (i < tpl.length) {
            val c = tpl[i]
            if (c == '{') {
                val end = tpl.indexOf('}', i)
                if (end > i) {
                    val inner = tpl.substring(i + 1, end)
                    val idx = if (inner.isEmpty()) next++ else inner.toIntOrNull()?.minus(1)
                    if (idx != null) {
                        sb.append(args.getOrElse(idx) { "" })
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}

/** AlertDialog.Builder that keeps titles, messages, buttons, and list items as plain strings. */
class TBuilder(context: Context, theme: Int) : AlertDialog.Builder(context, theme) {
    override fun setTitle(title: CharSequence?): AlertDialog.Builder = super.setTitle(L.t(title))
    override fun setMessage(message: CharSequence?): AlertDialog.Builder = super.setMessage(L.t(message))
    override fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): AlertDialog.Builder =
        super.setPositiveButton(L.t(text), listener)
    override fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): AlertDialog.Builder =
        super.setNegativeButton(L.t(text), listener)
    override fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): AlertDialog.Builder =
        super.setNeutralButton(L.t(text), listener)
    override fun setItems(items: Array<out CharSequence>?, listener: DialogInterface.OnClickListener?): AlertDialog.Builder =
        super.setItems(items?.map { L.t(it) }?.toTypedArray(), listener)
    override fun setSingleChoiceItems(
        items: Array<out CharSequence>?, checkedItem: Int, listener: DialogInterface.OnClickListener?,
    ): AlertDialog.Builder = super.setSingleChoiceItems(items?.map { L.t(it) }?.toTypedArray(), checkedItem, listener)
}

/** Toast with plain text. */
object LToast {
    fun makeText(c: Context, text: CharSequence?, duration: Int): Toast = Toast.makeText(c, L.t(text), duration)
}
