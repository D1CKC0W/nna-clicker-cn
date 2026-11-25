package com.nnazuna.nnaclicker

import android.app.Activity
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** A short paged guide: record, play, edit, image steps, repeats, and the run log. */
class TutorialActivity : Activity() {

    private class Page(val icon: Icon, val title: String, val body: String)

    private val pages = listOf(
        Page(Icon.PLAY, "欢迎使用 NNA Clicker",
            "NNA Clicker 重复你在屏幕上的操作：点击、滑动等。这份简短指南约一分钟带你了解基础。"),
        Page(Icon.RECORD, "1. 录制",
            "点击显示面板，然后打开你的游戏。按悬浮面板上的红色按钮，做你的点击和滑动，然后按停止。录制会自动保存并选中。"),
        Page(Icon.PAUSE, "2. 播放、暂停、停止",
            "在面板上按播放。用暂停休息一下，从同一步继续。按停止或音量下键随时结束。在主界面的播放下设置重复和速度。"),
        Page(Icon.LIST, "3. 编辑",
            "在某条录制上点编辑可更改延时、移动或删除步骤，并用添加步骤新增。用某步骤 ⋮ 菜单里的从此处播放测试一段。"),
        Page(Icon.CAMERA, "4. 图像步骤",
            "录制时按相机按钮添加图像步骤。在编辑器里围绕一个按钮或图标画框。播放时即可等待它、在任意位置点击它，或等待它消失。"),
        Page(Icon.JUMP, "5. 重复与选择",
            "在某步骤上打开 ⋮ 并选择重复步骤… 以将一段运行多次。图像步骤可在找到或未找到时选择下一步执行什么，例如：输了时点重试。"),
        Page(Icon.IMAGE, "6. 出错时怎么办",
            "如果未找到图像，播放会停止，屏幕会保存到运行日志。打开它查看当时屏幕内容并一键修复步骤。"),
        Page(Icon.TAP, "就绪",
            "没时间录制？用主界面的自动点击器重复点击。你可以从主界面的教程重新打开本指南。"),
    )

    private lateinit var k: Kit
    private val p get() = k.p
    private var index = 0
    private lateinit var iconBox: FrameLayout
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var dots: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        k = Kit(this)
        Ui.styleWindow(this, p)
        Storage(this).tutorialSeen = true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(24f), k.dp(16f), k.dp(24f), k.dp(28f))
            setBackgroundColor(p.bg)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        top.addView(k.textButton("跳过", p.muted) { finish() })
        root.addView(top)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        iconBox = FrameLayout(this).apply { background = k.rounded(p.surface, 48f) }
        content.addView(iconBox, LinearLayout.LayoutParams(k.dp(96f), k.dp(96f)).apply {
            topMargin = k.dp(24f); bottomMargin = k.dp(28f)
        })
        title = k.text("", 24f, 2).apply { gravity = Gravity.CENTER }
        content.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body = k.text("", 16f, 0, p.muted).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
            setPadding(0, k.dp(14f), 0, 0)
        }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = k.dp(20f)
        })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        back = k.textButton("返回", p.play) { go(index - 1) }.apply {
            setPadding(k.dp(8f), k.dp(14f), k.dp(8f), k.dp(14f))
        }
        bar.addView(back, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        next = k.pillButton("下一步", p.play) { if (index < pages.size - 1) go(index + 1) else finish() }
        bar.addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        root.addView(bar)

        // Swipe left or right to change pages.
        val swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < abs(vy) || abs(vx) < 600) return false
                go(if (vx < 0) index + 1 else index - 1)
                return true
            }
        })
        content.setOnTouchListener { _, e -> swipe.onTouchEvent(e); true }

        setContentView(root)
        go(0)
    }

    private fun go(i: Int) {
        if (i !in pages.indices) return
        index = i
        val pg = pages[i]
        iconBox.removeAllViews()
        iconBox.addView(k.icon(pg.icon, if (pg.icon == Icon.RECORD) p.rec else p.play, 44f),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        title.text = L.t(pg.title)
        body.text = L.t(pg.body)
        back.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        next.text = L.t(if (i == pages.size - 1) "开始" else "下一步")
        dots.removeAllViews()
        for (d in pages.indices) {
            dots.addView(View(this).apply { background = k.rounded(if (d == i) p.play else p.line, 4f) },
                LinearLayout.LayoutParams(k.dp(if (d == i) 20f else 8f), k.dp(8f)).apply { setMargins(k.dp(3f), 0, k.dp(3f), 0) })
        }
    }
}
