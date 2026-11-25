package com.nnazuna.nnaclicker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pick a saved screenshot, with thumbnails and delete. */
object SnapPicker {

    fun show(a: Activity, k: Kit, storage: Storage, title: String, onPick: ((File) -> Unit)?) {
        val p = k.p
        val list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(12f), k.dp(4f), k.dp(12f), k.dp(4f))
        }
        lateinit var dialog: AlertDialog
        val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

        fun render() {
            list.removeAllViews()
            val snaps = storage.snaps()
            val usage = storage.snapUsage()
            if (snaps.isEmpty()) {
                list.addView(k.text(
                    "暂无截图。打开游戏，然后按悬浮面板上的相机按钮。",
                    15f, 0, p.muted,
                ).apply { setPadding(k.dp(8f), k.dp(12f), k.dp(8f), k.dp(12f)) })
                return
            }
            for (f in snaps) {
                val used = usage[f.name] ?: 0
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(k.dp(4f), k.dp(6f), 0, k.dp(6f))
                    background = k.ripple(null, k.dp(12f).toFloat())
                    if (onPick != null) setOnClickListener {
                        dialog.dismiss()
                        onPick(f)
                    }
                }
                val thumb = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 })
                row.addView(ImageView(a).apply {
                    setImageBitmap(thumb)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = k.rounded(p.bg, 8f)
                    clipToOutline = true
                }, LinearLayout.LayoutParams(k.dp(96f), k.dp(56f)).apply { rightMargin = k.dp(12f) })
                val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                col.addView(k.text(fmt.format(Date(f.lastModified())), 15f, 1))
                col.addView(k.text(
                    if (used > 0) "被 $used 个图像步骤使用" + (if (used > 1) "s" else "") else "未使用",
                    13f, 0, p.muted,
                ).apply { setPadding(0, k.dp(3f), 0, 0) })
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(k.icon(Icon.TRASH, p.rec, 22f).apply {
                    minimumWidth = k.dp(44f); minimumHeight = k.dp(44f)
                    background = k.ripple(null, k.dp(22f).toFloat())
                    setOnClickListener {
                        if (used > 0) {
                            TBuilder(a, Ui.dialogTheme(p))
                                .setTitle("删除截图？")
                                .setMessage("使用它的图像步骤仍可工作，但没有新截图无法更改其区域。")
                                .setPositiveButton("删除") { _, _ -> storage.deleteSnap(f.name); render() }
                                .setNegativeButton("取消", null)
                                .show()
                        } else {
                            storage.deleteSnap(f.name)
                            render()
                        }
                    }
                })
                list.addView(row)
            }
        }

        render()
        dialog = TBuilder(a, Ui.dialogTheme(p))
            .setTitle(title)
            .setView(ScrollView(a).apply { addView(list) })
            .setNegativeButton("关闭", null)
            .setNeutralButton("删除未使用", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val usage = storage.snapUsage()
                val unused = storage.snaps().filter { (usage[it.name] ?: 0) == 0 }
                if (unused.isEmpty()) return@setOnClickListener
                TBuilder(a, Ui.dialogTheme(p))
                    .setTitle("删除 ${unused.size} 张未使用截图" + (if (unused.size > 1) "s" else "") + "?")
                    .setPositiveButton("删除") { _, _ -> unused.forEach { storage.deleteSnap(it.name) }; render() }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        dialog.show()
    }
}
