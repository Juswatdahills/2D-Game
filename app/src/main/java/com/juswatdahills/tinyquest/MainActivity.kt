package com.juswatdahills.tinyquest

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG immersiveSticky or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(GameView())
    }
}

private class GameView : View(null) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var px = 0f
    private var py = 0f
    private var hp = 100
    private var xp = 0
    private var coins = 0
    private var facing = 1f
    private var message = "Find the village elder and defeat the slimes!"
    private var questDone = false
    private var gameOver = false
    private var lastAttack = 0L
    private val enemies = mutableListOf<Enemy>()
    private data class Enemy(var x: Float, var y: Float, var hp: Int = 2, var alive: Boolean = true)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (px == 0f) { px = width * .5f; py = height * .58f
            enemies.add(Enemy(width * .25f, height * .35f))
            enemies.add(Enemy(width * .72f, height * .32f))
            enemies.add(Enemy(width * .62f, height * .72f))
        }
        val w = width.toFloat(); val h = height.toFloat()
        p.color = Color.rgb(91, 151, 91); c.drawColor(Color.rgb(75, 133, 79))
        p.color = Color.rgb(112, 164, 102)
        for (i in 0..12) for (j in 0..7) {
            if ((i + j) % 2 == 0) c.drawRect(i*w/13, j*h/8, (i+1)*w/13, (j+1)*h/8, p)
        }
        // path and village
        p.color = Color.rgb(194, 168, 119); c.drawRect(w*.43f, 0f, w*.57f, h, p)
        c.drawRect(0f, h*.48f, w, h*.61f, p)
        p.color = Color.rgb(105, 70, 45); c.drawRect(w*.08f,h*.12f,w*.22f,h*.30f,p)
        p.color = Color.rgb(181, 75, 55)
        val roof = android.graphics.Path().apply { moveTo(w*.06f,h*.12f); lineTo(w*.15f,h*.02f); lineTo(w*.24f,h*.12f); close() }
        c.drawPath(roof,p)
        p.color = Color.WHITE; p.textSize = 16f; c.drawText("VILLAGE", w*.105f,h*.34f,p)
        // elder
        p.color = Color.rgb(70, 65, 125); c.drawCircle(w*.16f,h*.42f,15f,p)
        p.color = Color.rgb(240, 205, 160); c.drawCircle(w*.16f,h*.40f,8f,p)
        // enemies
        enemies.filter { it.alive }.forEach { e ->
            p.color = Color.rgb(75, 210, 115); c.drawCircle(e.x,e.y,17f,p)
            p.color = Color.BLACK; c.drawCircle(e.x-6,e.y-3,2.5f,p); c.drawCircle(e.x+6,e.y-3,2.5f,p)
            p.color = Color.RED; c.drawRect(e.x-15,e.y-25,e.x+15,e.y-21,p)
            p.color = Color.GREEN; c.drawRect(e.x-15,e.y-25,e.x-15+30f*e.hp/2f,e.y-21,p)
        }
        // player
        p.color = Color.rgb(45, 82, 190); c.drawCircle(px,py,16f,p)
        p.color = Color.rgb(245, 205, 160); c.drawCircle(px,py-7,9f,p)
        p.color = Color.rgb(40, 35, 30); c.drawCircle(px+facing*4,py-9,2f,p)
        // HUD
        p.color = Color.argb(190,20,25,35); c.drawRoundRect(12f,12f,230f,91f,12f,12f,p)
        p.color = Color.WHITE; p.textSize = 19f; c.drawText("TINY QUEST",24f,37f,p)
        p.textSize = 15f; c.drawText("HP $hp/100   XP $xp   Coins $coins",24f,62f,p)
        c.drawText(message,24f,h-18f,p)
        drawButton(c,w-100f,h-85f,"ATTACK")
        drawButton(c,85f,h-75f,"MOVE")
        if (gameOver) {
            p.color = Color.argb(210,0,0,0); c.drawRect(0f,0f,w,h,p)
            p.color = Color.WHITE; p.textSize = 36f; c.drawText("YOU WERE DEFEATED",w*.28f,h*.45f,p)
            p.textSize = 20f; c.drawText("Tap anywhere to restart",w*.37f,h*.55f,p)
        }
        if (xp >= 30 && !questDone) { questDone = true; message = "Quest complete! You are a slime hunter!" }
        postInvalidateDelayed(50)
    }

    private fun drawButton(c: Canvas,x:Float,y:Float,label:String) {
        p.color = Color.argb(175,25,30,40); c.drawRoundRect(x-48,y-28,x+48,y+28,14f,14f,p)
        p.color = Color.WHITE; p.textSize = 15f; c.drawText(label,x-36,y+5,p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            if (gameOver) { hp=100; xp=0; coins=0; gameOver=false; questDone=false
                px=width*.5f; py=height*.58f; enemies.clear()
                enemies.add(Enemy(width*.25f,height*.35f)); enemies.add(Enemy(width*.72f,height*.32f)); enemies.add(Enemy(width*.62f,height*.72f))
                message="Find the village elder and defeat the slimes!"; invalidate(); return true }
            val x=e.x; val y=e.y
            if (x > width*.72f && y > height*.68f && e.action == MotionEvent.ACTION_DOWN) attack()
            else if (y > height*.65f && x < width*.3f) {
                px=(px + if (x < width*.15f) -18f else 18f).coerceIn(20f,width-20f)
                facing=if (x < width*.15f) -1f else 1f
            } else if (y < height*.35f) py=(py-14f).coerceAtLeast(25f)
            else if (y > height*.65f) py=(py+14f).coerceAtMost(height-30f)
            else { px=(px + if (x < px) -16f else 16f).coerceIn(20f,width-20f); facing=if(x<px)-1f else 1f }
            invalidate()
        }
        return true
    }

    private fun attack() {
        if (System.currentTimeMillis()-lastAttack < 350) return
        lastAttack=System.currentTimeMillis()
        val target=enemies.filter { it.alive && hypot(it.x-px,it.y-py)<105f }
            .minByOrNull { hypot(it.x-px,it.y-py) }
        if (target != null) {
            target.hp--
            if (target.hp <= 0) { target.alive=false; xp+=10; coins+=5; message="Slime defeated! +10 XP, +5 coins" }
            else message="Hit! Keep attacking!"
        } else message="No slime nearby. Move closer!"
        if (enemies.none { it.alive } && hp > 0) message="All slimes defeated! Visit the elder to claim your quest."
        invalidate()
    }
}
