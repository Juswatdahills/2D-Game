package com.juswatdahills.tinyquest

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(GameView(this))
    }
}

private class GameView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var playerX = 160f
    private var playerY = 0f
    private var velocityY = 0f
    private var cameraX = 0f
    private var facing = 1f
    private var hp = 100
    private var mana = 100
    private var xp = 0
    private var coins = 0
    private var message = "Explore the land. Defeat the slimes!"
    private var gameOver = false
    private var lastAttack = 0L
    private var lastSkill = 0L
    private var lastFrame = 0L
    private var joyX = 0f
    private var jumpHeld = false
    private var onGround = false
    private val groundY get() = height * .78f
    private val worldWidth = 2600f
    private val enemies = mutableListOf<Enemy>()
    private val shots = mutableListOf<Shot>()
    private data class Enemy(var x: Float, var hp: Int = 3, var alive: Boolean = true, var dir: Float = -1f)
    private data class Shot(var x: Float, var y: Float, var dir: Float, var life: Int = 70)

    init { isFocusable = true }

    private fun reset() {
        playerX = 160f; playerY = groundY - 42f; velocityY = 0f; cameraX = 0f
        hp = 100; mana = 100; xp = 0; coins = 0; gameOver = false
        enemies.clear(); shots.clear()
        for (x in listOf(560f, 920f, 1320f, 1770f, 2200f)) enemies.add(Enemy(x))
        message = "Explore the land. Defeat the slimes!"
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (playerY == 0f) reset()
        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) .016f else ((now-lastFrame).coerceIn(1L,40L)/1000f)
        lastFrame = now
        if (!gameOver) updateWorld(dt)
        drawWorld(c)
        postInvalidateDelayed(16)
    }

    private fun updateWorld(dt: Float) {
        val currentTime = System.currentTimeMillis()
        playerX = (playerX + joyX * 260f * dt).coerceIn(25f, worldWidth-25f)
        if (joyX != 0f) facing = if (joyX > 0f) 1f else -1f
        velocityY += 1450f * dt
        playerY += velocityY * dt
        if (playerY + 42f >= groundY) {
            playerY = groundY - 42f; velocityY = 0f; onGround = true
        } else onGround = false
        cameraX = (playerX - width*.38f).coerceIn(0f, worldWidth-width.toFloat())
        enemies.filter { it.alive }.forEach { enemy ->
            if (abs(enemy.x-playerX) < 310f) {
                enemy.dir = if (playerX < enemy.x) -1f else 1f
                enemy.x += enemy.dir * 48f * dt
                if (abs(enemy.x-playerX) < 43f && currentTime-lastAttack > 850) {
                    hp = (hp-5).coerceAtLeast(0)
                    lastAttack = currentTime
                    if (hp == 0) { gameOver = true; message = "You were defeated!" }
                }
            }
        }
        val iterator = shots.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            shot.x += shot.dir * 520f * dt
            shot.life--
            val hit = enemies.firstOrNull { it.alive && abs(it.x-shot.x) < 34f }
            if (hit != null) {
                hit.hp -= 2
                if (hit.hp <= 0) { hit.alive = false; xp += 15; coins += 5; message = "Slime defeated! +15 XP, +5 coins" }
                iterator.remove()
            } else if (shot.life <= 0 || shot.x < 0 || shot.x > worldWidth) iterator.remove()
        }
        if (mana < 100) mana = (mana + 5f*dt).toInt().coerceAtMost(100)
        if (enemies.none { it.alive }) message = "All slimes defeated! You cleared the stage!"
    }

    private fun drawWorld(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.rgb(116, 190, 231))
        // distant hills
        p.color = Color.rgb(104, 174, 132)
        for (i in -1..7) {
            val x = i*430f - (cameraX*.22f % 430f)
            c.drawCircle(x, groundY-80f, 180f, p)
        }
        c.save()
        c.translate(-cameraX, 0f)
        // ground and dirt
        p.color = Color.rgb(82, 164, 75); c.drawRect(cameraX,groundY, cameraX+w, h,p)
        p.color = Color.rgb(119, 79, 48); c.drawRect(cameraX,groundY+28f,cameraX+w,h,p)
        // platforms and scenery
        for (x in 300..2400 step 400) {
            p.color = Color.rgb(115, 75, 45); c.drawRoundRect(x.toFloat(),groundY-110f,x+150f,groundY-88f,8f,8f,p)
            p.color = Color.rgb(75, 170, 74); c.drawRect(x.toFloat(),groundY-116f,x+150f,groundY-106f,p)
        }
        for (x in 180..2500 step 520) {
            p.color = Color.rgb(112, 77, 48); c.drawRect(x.toFloat(),groundY-65f,x+22f,groundY,p)
            p.color = Color.rgb(42, 133, 60); c.drawCircle(x+10f,groundY-82f,48f,p)
            p.color = Color.rgb(55, 155, 68); c.drawCircle(x-18f,groundY-65f,30f,p)
            c.drawCircle(x+36f,groundY-66f,30f,p)
        }
        // enemies
        enemies.filter { it.alive }.forEach { e ->
            p.color = Color.rgb(54, 193, 105); c.drawOval(e.x-22f,groundY-43f,e.x+22f,groundY-5f,p)
            p.color = Color.WHITE; c.drawCircle(e.x-8f,groundY-31f,5f,p); c.drawCircle(e.x+8f,groundY-31f,5f,p)
            p.color = Color.BLACK; c.drawCircle(e.x-8f,groundY-31f,2.5f,p); c.drawCircle(e.x+8f,groundY-31f,2.5f,p)
            p.color = Color.RED; c.drawRect(e.x-19f,groundY-52f,e.x+19f,groundY-47f,p)
            p.color = Color.GREEN; c.drawRect(e.x-19f,groundY-52f,e.x-19f+38f*e.hp/3f,groundY-47f,p)
        }
        // magic projectiles
        shots.forEach { s ->
            p.color = Color.rgb(91, 220, 255); c.drawCircle(s.x,s.y,12f,p)
            p.color = Color.WHITE; c.drawCircle(s.x,s.y,5f,p)
        }
        // player hero
        val x = playerX
        p.color = Color.rgb(42, 75, 190); c.drawRoundRect(x-17f,playerY-24f,x+17f,playerY+42f,8f,8f,p)
        p.color = Color.rgb(241, 192, 145); c.drawCircle(x,playerY-31f,16f,p)
        p.color = Color.rgb(66, 43, 28); c.drawArc(x-17f,playerY-47f,x+17f,playerY-19f,180f,180f,true,p)
        p.color = Color.BLACK; c.drawCircle(x+facing*6f,playerY-33f,2.5f,p)
        c.restore()

        // HUD
        p.color = Color.argb(205,20,28,42); c.drawRoundRect(14f,12f,250f,92f,12f,12f,p)
        p.color = Color.WHITE; p.textSize = 18f; c.drawText("TINY QUEST",26f,35f,p)
        p.textSize = 14f; c.drawText("HP $hp   XP $xp   Coins $coins",26f,58f,p)
        p.color = Color.rgb(70,70,80); c.drawRoundRect(26f,68f,220f,78f,5f,5f,p)
        p.color = Color.rgb(70,210,255); c.drawRoundRect(26f,68f,26f+194f*mana/100f,78f,5f,5f,p)
        p.color = Color.WHITE; p.textSize = 12f; c.drawText("MANA",26f,89f,p)

        // Virtual joystick
        val baseX = 100f; val baseY = h-100f
        p.color = Color.argb(95,25,35,50); c.drawCircle(baseX,baseY,60f,p)
        p.color = Color.argb(190,220,230,240)
        c.drawCircle(baseX+joyX*34f,baseY,27f,p)
        p.color = Color.rgb(55,70,90); c.drawCircle(baseX+joyX*34f,baseY,13f,p)
        drawControl(c,w-190f,h-92f,"JUMP")
        drawControl(c,w-95f,h-92f,"HIT")
        drawControl(c,w-95f,h-190f,"SKILL")
        p.color = Color.WHITE; p.textSize = 14f
        c.drawText(message, w*.34f, 28f, p)
        if (gameOver) {
            p.color = Color.argb(210,0,0,0); c.drawRect(0f,0f,w,h,p)
            p.color = Color.WHITE; p.textSize = 34f; c.drawText("GAME OVER",w*.39f,h*.44f,p)
            p.textSize = 19f; c.drawText("Tap anywhere to restart",w*.38f,h*.54f,p)
        }
    }

    private fun drawControl(c: Canvas,x:Float,y:Float,label:String) {
        p.color = Color.argb(165,25,35,55); c.drawCircle(x,y,43f,p)
        p.color = Color.WHITE; p.textSize = if (label=="SKILL") 13f else 14f
        val tw=p.measureText(label); c.drawText(label,x-tw/2f,y+5f,p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (gameOver && e.actionMasked == MotionEvent.ACTION_DOWN) { reset(); invalidate(); return true }
        val joyCenter = 100f
        val joyY = height-100f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                joyX = 0f
                for (i in 0 until e.pointerCount) {
                    val x=e.getX(i); val y=e.getY(i)
                    if (x < width*.38f && y > height*.58f) {
                        joyX = ((x-joyCenter)/60f).coerceIn(-1f,1f)
                    }
                }
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    val index=e.actionIndex
                    val x=e.getX(index); val y=e.getY(index)
                    if (x > width-145f && y > height-245f && y < height-125f) useSkill()
                    else if (x > width-145f && y > height-140f) attack()
                    else if (x > width-245f && x < width-140f && y > height-140f) jump()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                joyX = 0f
                for (i in 0 until e.pointerCount) {
                    if (i == e.actionIndex) continue
                    val x=e.getX(i); val y=e.getY(i)
                    if (x < width*.38f && y > height*.58f) joyX=((x-joyCenter)/60f).coerceIn(-1f,1f)
                }
            }
        }
        invalidate()
        return true
    }

    private fun jump() {
        if (onGround && !gameOver) { velocityY = -560f; onGround = false }
    }

    private fun attack() {
        val now=System.currentTimeMillis()
        if (now-lastAttack < 300 || gameOver) return
        lastAttack=now
        val target=enemies.firstOrNull { it.alive && abs(it.x-playerX)<85f }
        if (target != null) {
            target.hp--
            if (target.hp<=0) { target.alive=false; xp+=10; coins+=3; message="Slime defeated! +10 XP, +3 coins" }
            else message="Sword hit!"
        } else message="No enemy in range!"
    }

    private fun useSkill() {
        val now=System.currentTimeMillis()
        if (gameOver || now-lastSkill<700) return
        if (mana<25) { message="Not enough mana!"; return }
        mana-=25; lastSkill=now
        shots.add(Shot(playerX+facing*30f,playerY-15f,facing))
        message="Magic bolt!"
    }
}
