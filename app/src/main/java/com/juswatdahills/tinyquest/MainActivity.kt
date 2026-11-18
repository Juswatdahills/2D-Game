package com.juswatdahills.tinyquest

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(GameView(this))
    }
}

private class GameView(context: Context) : View(context) {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val worldWidth = 4800f
    private val ground get() = height * .79f
    private var px = 150f
    private var py = 0f
    private var vy = 0f
    private var camera = 0f
    private var facing = 1f
    private var hp = 100
    private var mana = 100f
    private var coins = 0
    private var score = 0
    private var stage = 0
    private var message = "RUN • JUMP • FIGHT"
    private var messageTimer = 2.5f
    private var gameOver = false
    private var won = false
    private var grounded = false
    private var joy = 0f
    private var lastFrame = 0L
    private var attackCooldown = 0f
    private var skillCooldown = 0f
    private var hurtCooldown = 0f
    private var attackFlash = 0f
    private var animTime = 0f
    private val enemies = mutableListOf<Enemy>()
    private val shots = mutableListOf<Shot>()
    private val enemyShots = mutableListOf<Shot>()
    private val platforms = mutableListOf<Platform>()
    private val pickups = mutableListOf<Pickup>()
    private data class Enemy(var x: Float, var y: Float, var hp: Int, var kind: Int, var alive: Boolean = true, var dir: Float = -1f, var cooldown: Float = 0f)
    private data class Shot(var x: Float, var y: Float, var dir: Float, var life: Float = 2f, var hostile: Boolean = false)
    private data class Platform(val x: Float, val y: Float, val w: Float)
    private data class Pickup(var x: Float, var y: Float, var kind: Int, var taken: Boolean = false)

    init { isFocusable = true; reset() }

    private fun reset() {
        px = 150f; py = ground - 44f; vy = 0f; camera = 0f
        hp = 100; mana = 100f; coins = 0; score = 0; stage = 0
        gameOver = false; won = false; joy = 0f
        enemies.clear(); shots.clear(); enemyShots.clear(); platforms.clear(); pickups.clear()
        val platformData = listOf(
            Triple(420f, .58f, 150f), Triple(720f, .49f, 130f), Triple(1040f, .60f, 170f),
            Triple(1480f, .55f, 160f), Triple(1810f, .46f, 150f), Triple(2220f, .60f, 180f),
            Triple(2670f, .54f, 170f), Triple(3020f, .45f, 150f), Triple(3440f, .58f, 180f),
            Triple(3820f, .50f, 160f), Triple(4210f, .60f, 180f)
        )
        platformData.forEach { platforms.add(Platform(it.first, height * it.second, it.third)) }
        listOf(530f, 880f, 1250f, 1640f, 2040f, 2470f, 2860f, 3260f, 3660f, 4050f, 4430f).forEachIndexed { i, x ->
            enemies.add(Enemy(x, ground - 34f, if (i == 10) 18 else 3 + i % 2, if (i == 10) 2 else i % 2))
        }
        for (x in listOf(350f, 650f, 970f, 1380f, 1740f, 2140f, 2580f, 2940f, 3370f, 3750f, 4150f)) {
            pickups.add(Pickup(x, ground - 65f, 0))
        }
        pickups.add(Pickup(1120f, ground - 150f, 1))
        pickups.add(Pickup(3140f, ground - 145f, 1))
        say("THE GREEN WILDS • STAGE 1")
    }

    private fun say(text: String) { message = text; messageTimer = 2.2f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) .016f else ((now - lastFrame).coerceIn(1L, 34L) / 1000f)
        lastFrame = now
        if (py == 0f) py = ground - 44f
        if (!gameOver && !won) update(dt)
        drawScene(canvas)
        postInvalidateDelayed(16)
    }

    private fun update(dt: Float) {
        animTime += dt
        attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        skillCooldown = (skillCooldown - dt).coerceAtLeast(0f)
        hurtCooldown = (hurtCooldown - dt).coerceAtLeast(0f)
        attackFlash = (attackFlash - dt).coerceAtLeast(0f)
        messageTimer = (messageTimer - dt).coerceAtLeast(0f)
        mana = (mana + 9f * dt).coerceAtMost(100f)

        val oldBottom = py + 44f
        px = (px + joy * 300f * dt).coerceIn(20f, worldWidth - 35f)
        if (joy != 0f) facing = if (joy > 0f) 1f else -1f
        vy += 1500f * dt
        py += vy * dt
        grounded = false
        if (py + 44f >= ground) { py = ground - 44f; vy = 0f; grounded = true }
        if (vy >= 0f) {
            platforms.forEach { platform ->
                if (px + 13f > platform.x && px - 13f < platform.x + platform.w &&
                    oldBottom <= platform.y + 5f && py + 44f >= platform.y) {
                    py = platform.y - 44f; vy = 0f; grounded = true
                }
            }
        }
        if (py > height + 120f) hurt(30)
        camera = (px - width * .36f).coerceIn(0f, worldWidth - width.toFloat())

        enemies.filter { it.alive }.forEach { e ->
            e.cooldown = (e.cooldown - dt).coerceAtLeast(0f)
            val distance = px - e.x
            if (abs(distance) < 390f) {
                e.dir = if (distance < 0f) -1f else 1f
                e.x += e.dir * (if (e.kind == 2) 32f else 65f) * dt
                if (e.kind == 1 && e.cooldown <= 0f && abs(distance) < 300f) {
                    enemyShots.add(Shot(e.x, e.y - 25f, if (distance < 0) -1f else 1f, 2.8f, true))
                    e.cooldown = 2.1f
                }
                if (abs(distance) < (if (e.kind == 2) 62f else 39f) && hurtCooldown <= 0f) hurt(if (e.kind == 2) 18 else 9)
            }
        }

        val shotIterator = shots.iterator()
        while (shotIterator.hasNext()) {
            val s = shotIterator.next()
            s.x += s.dir * 590f * dt; s.life -= dt
            val hit = enemies.firstOrNull { it.alive && abs(it.x - s.x) < (if (it.kind == 2) 58f else 30f) && abs(it.y - s.y) < 65f }
            if (hit != null) {
                hit.hp -= 2
                if (hit.hp <= 0) defeat(hit)
                shotIterator.remove()
            } else if (s.life <= 0f || s.x < 0f || s.x > worldWidth) shotIterator.remove()
        }
        val enemyIterator = enemyShots.iterator()
        while (enemyIterator.hasNext()) {
            val s = enemyIterator.next()
            s.x += s.dir * 250f * dt; s.life -= dt
            if (abs(s.x - px) < 25f && abs(s.y - (py + 20f)) < 38f && hurtCooldown <= 0f) {
                hurt(12); enemyIterator.remove()
            } else if (s.life <= 0f || s.x < 0f || s.x > worldWidth) enemyIterator.remove()
        }
        pickups.filter { !it.taken }.forEach { item ->
            if (abs(item.x - px) < 30f && abs(item.y - (py + 20f)) < 55f) {
                item.taken = true
                if (item.kind == 0) { coins += 1; score += 100; say("+1 COIN") }
                else { hp = (hp + 30).coerceAtMost(100); say("HEALTH RESTORED") }
            }
        }
        stage = (px / 1600f).toInt().coerceIn(0, 2)
        if (px > 4650f && enemies.none { it.alive }) { won = true; say("WORLD CLEARED!") }
        if (px > 4650f && enemies.any { it.alive && it.kind == 2 }) say("DEFEAT THE FOREST GUARDIAN!")
    }

    private fun hurt(amount: Int) {
        if (hurtCooldown > 0f) return
        hp = (hp - amount).coerceAtLeast(0); hurtCooldown = 1f
        if (hp == 0) { gameOver = true; say("YOU HAVE FALLEN") }
    }

    private fun defeat(e: Enemy) {
        e.alive = false; score += if (e.kind == 2) 2500 else 250
        coins += if (e.kind == 2) 10 else 2
        say(if (e.kind == 2) "GUARDIAN DEFEATED! +10 COINS" else "ENEMY DEFEATED! +2 COINS")
    }

    private fun jump() {
        if (!gameOver && !won && grounded) { vy = -610f; grounded = false }
    }

    private fun attack() {
        if (gameOver || won || attackCooldown > 0f) return
        attackCooldown = .34f; attackFlash = .18f
        val target = enemies.firstOrNull { it.alive && (it.x - px) * facing in 0f..105f && abs(it.y - py) < 90f }
        if (target != null) { target.hp -= 2; if (target.hp <= 0) defeat(target) else say("SWORD STRIKE!") }
        else say("SWING!")
    }

    private fun skill() {
        if (gameOver || won || skillCooldown > 0f) return
        if (mana < 25f) { say("NOT ENOUGH ENERGY"); return }
        mana -= 25f; skillCooldown = .55f
        shots.add(Shot(px + facing * 25f, py + 5f, facing))
        say("ARCANE BLAST!")
    }

    private fun color(c: Canvas, col: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = col; c.drawRect(l, t, r, b, paint)
    }
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int) = color(c, col, x, y, x + w, y + h)
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, col: Int = Color.WHITE) {
        paint.color = col; paint.textSize = size; paint.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD); c.drawText(value, x, y, paint)
    }

    private fun drawScene(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val sky = when (stage) { 0 -> Color.rgb(100, 185, 224); 1 -> Color.rgb(80, 102, 145); else -> Color.rgb(79, 58, 103) }
        c.drawColor(sky)
        drawBackground(c, w, h)
        c.save(); c.translate(-camera, 0f)
        drawGround(c, w, h)
        platforms.forEach { drawPlatform(c, it) }
        pickups.filter { !it.taken }.forEach { item -> drawPickup(c, item) }
        enemies.filter { it.alive }.forEach { drawEnemy(c, it) }
        shots.forEach { drawShot(c, it, false) }
        enemyShots.forEach { drawShot(c, it, true) }
        drawHero(c)
        c.restore()
        drawHud(c, w, h)
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        // Distant parallax mountains
        val shift = camera * .18f
        for (i in -1..7) {
            val x = i * 390f - (shift % 390f)
            val base = ground - 55f
            val mountain = if (stage == 0) Color.rgb(71, 139, 111) else if (stage == 1) Color.rgb(65, 76, 112) else Color.rgb(94, 63, 111)
            rect(c, x + 55f, base - 160f, 170f, 160f, mountain)
            rect(c, x + 25f, base - 110f, 230f, 110f, mountain)
            rect(c, x + 85f, base - 205f, 110f, 50f, mountain)
            rect(c, x + 105f, base - 235f, 70f, 30f, mountain)
            rect(c, x + 125f, base - 260f, 30f, 25f, mountain)
            val cloud = Color.argb(110, 255, 255, 255)
            rect(c, x + 25f, 85f + (i % 3) * 25f, 70f, 12f, cloud)
            rect(c, x + 45f, 73f + (i % 3) * 25f, 40f, 12f, cloud)
        }
        // Parallax trees and ruins
        for (i in -1..10) {
            val x = i * 250f - (camera * .42f % 250f)
            val trunk = if (stage == 0) Color.rgb(105, 67, 42) else Color.rgb(48, 48, 64)
            val leaves = if (stage == 0) Color.rgb(34, 112, 66) else if (stage == 1) Color.rgb(63, 66, 92) else Color.rgb(103, 53, 83)
            rect(c, x + 65f, ground - 135f, 18f, 135f, trunk)
            rect(c, x + 30f, ground - 185f, 90f, 55f, leaves)
            rect(c, x + 45f, ground - 205f, 60f, 25f, leaves)
            rect(c, x + 10f, ground - 160f, 35f, 35f, leaves)
            rect(c, x + 100f, ground - 160f, 35f, 35f, leaves)
        }
        // Tiny stars in later worlds
        if (stage > 0) for (i in 0..28) {
            val x = ((i * 173f - camera * .08f) % w + w) % w
            rect(c, x, 35f + (i * 47 % 150), 3f, 3f, Color.rgb(255, 238, 171))
        }
    }

    private fun drawGround(c: Canvas, w: Float, h: Float) {
        val top = when (stage) { 0 -> Color.rgb(71, 158, 74); 1 -> Color.rgb(83, 105, 121); else -> Color.rgb(126, 68, 86) }
        val dirt = when (stage) { 0 -> Color.rgb(105, 70, 48); 1 -> Color.rgb(61, 59, 78); else -> Color.rgb(74, 46, 70) }
        rect(c, camera - 2f, ground, w + 4f, h - ground + 20f, dirt)
        rect(c, camera - 2f, ground, w + 4f, 15f, top)
        for (x in (camera.toInt() / 48 * 48)..(camera.toInt() + w.toInt() + 48) step 48) {
            rect(c, x.toFloat(), ground + 22f, 24f, 5f, Color.argb(100, 25, 25, 30))
            rect(c, x + 28f, ground + 52f, 10f, 5f, Color.argb(80, 20, 20, 25))
        }
    }

    private fun drawPlatform(c: Canvas, p: Platform) {
        val stone = if (stage == 0) Color.rgb(119, 82, 54) else Color.rgb(82, 75, 91)
        rect(c, p.x, p.y, p.w, 17f, stone)
        rect(c, p.x, p.y, p.w, 5f, if (stage == 0) Color.rgb(87, 176, 76) else Color.rgb(150, 112, 112))
        for (i in 0 until (p.w / 28f).toInt()) rect(c, p.x + i * 28f + 5f, p.y + 9f, 12f, 4f, Color.argb(80, 20, 20, 25))
    }

    private fun drawPickup(c: Canvas, item: Pickup) {
        val bob = sin(animTime * 4f + item.x) * 4f
        if (item.kind == 0) {
            rect(c, item.x - 7f, item.y - 7f + bob, 14f, 14f, Color.rgb(255, 194, 43))
            rect(c, item.x - 4f, item.y - 10f + bob, 8f, 20f, Color.rgb(255, 229, 111))
            rect(c, item.x - 2f, item.y - 5f + bob, 4f, 10f, Color.rgb(180, 112, 24))
        } else {
            rect(c, item.x - 11f, item.y - 11f + bob, 22f, 22f, Color.rgb(220, 54, 74))
            rect(c, item.x - 3f, item.y - 8f + bob, 6f, 16f, Color.WHITE)
            rect(c, item.x - 8f, item.y - 3f + bob, 16f, 6f, Color.WHITE)
        }
    }

    private fun drawHero(c: Canvas) {
        val x = px; val y = py
        val blink = hurtCooldown > .0f && (animTime * 18f).toInt() % 2 == 0
        if (blink) return
        val bob = if (grounded && abs(joy) > .1f) (sin(animTime * 15f) * 3f) else 0f
        val skin = Color.rgb(245, 190, 139)
        val hair = Color.rgb(61, 39, 34)
        val coat = Color.rgb(43, 91, 205)
        val boot = Color.rgb(57, 43, 42)
        // cape
        rect(c, x - facing * 15f, y + 1f + bob, 9f, 23f, Color.rgb(176, 47, 58))
        // legs
        rect(c, x - 11f, y + 29f + bob, 9f, 12f, boot)
        rect(c, x + 2f, y + 29f - bob, 9f, 12f, boot)
        rect(c, x - 12f, y + 39f + bob, 12f, 5f, Color.rgb(30, 28, 38))
        rect(c, x + 1f, y + 39f - bob, 13f, 5f, Color.rgb(30, 28, 38))
        // torso and belt
        rect(c, x - 13f, y + 8f + bob, 26f, 23f, coat)
        rect(c, x - 13f, y + 25f + bob, 26f, 5f, Color.rgb(233, 174, 56))
        rect(c, x - 16f, y + 12f + bob, 5f, 15f, skin)
        rect(c, x + 11f, y + 12f + bob, 5f, 15f, skin)
        // face and hair
        rect(c, x - 11f, y - 12f + bob, 22f, 22f, skin)
        rect(c, x - 13f, y - 17f + bob, 26f, 9f, hair)
        rect(c, x - 13f, y - 9f + bob, 6f, 8f, hair)
        rect(c, x + facing * 5f, y - 4f + bob, 4f, 4f, Color.BLACK)
        rect(c, x - 3f, y + 2f + bob, 7f, 3f, Color.rgb(135, 58, 55))
        // sword and slash
        if (attackFlash > 0f) {
            rect(c, x + facing * 15f, y + 5f, facing * 35f, 5f, Color.rgb(245, 245, 255))
            rect(c, x + facing * 35f, y - 3f, facing * 5f, 20f, Color.rgb(111, 211, 255))
        } else {
            rect(c, x + facing * 15f, y + 17f, facing * 18f, 4f, Color.rgb(222, 226, 237))
        }
    }

    private fun drawEnemy(c: Canvas, e: Enemy) {
        val x = e.x; val y = e.y
        if (e.kind == 2) {
            // Large guardian boss
            rect(c, x - 39f, y - 76f, 78f, 76f, Color.rgb(104, 56, 132))
            rect(c, x - 31f, y - 89f, 18f, 22f, Color.rgb(72, 39, 103))
            rect(c, x + 13f, y - 89f, 18f, 22f, Color.rgb(72, 39, 103))
            rect(c, x - 24f, y - 57f, 13f, 13f, Color.rgb(255, 75, 68))
            rect(c, x + 11f, y - 57f, 13f, 13f, Color.rgb(255, 75, 68))
            rect(c, x - 26f, y - 20f, 52f, 10f, Color.rgb(57, 32, 81))
            rect(c, x - 52f, y - 104f, 104f, 7f, Color.rgb(55, 28, 40))
            rect(c, x - 52f, y - 104f, 104f * e.hp / 18f, 7f, Color.rgb(244, 66, 70))
        } else {
            val body = if (e.kind == 0) Color.rgb(56, 199, 117) else Color.rgb(177, 85, 199)
            rect(c, x - 19f, y - 27f, 38f, 27f, body)
            rect(c, x - 13f, y - 35f, 26f, 12f, body)
            rect(c, x - 11f, y - 23f, 5f, 7f, Color.WHITE)
            rect(c, x + 6f, y - 23f, 5f, 7f, Color.WHITE)
            rect(c, x - 9f, y - 21f, 3f, 5f, Color.BLACK)
            rect(c, x + 8f, y - 21f, 3f, 5f, Color.BLACK)
            rect(c, x - 15f, y - 39f, 30f, 3f, Color.rgb(55, 36, 46))
            rect(c, x - 15f, y - 39f, 30f * e.hp / 4f, 3f, Color.rgb(236, 71, 77))
        }
    }

    private fun drawShot(c: Canvas, s: Shot, hostile: Boolean) {
        val col = if (hostile) Color.rgb(255, 92, 77) else Color.rgb(86, 220, 255)
        rect(c, s.x - 8f, s.y - 5f, 16f, 10f, col)
        rect(c, s.x - 3f, s.y - 9f, 7f, 18f, Color.WHITE)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        rect(c, 12f, 12f, 244f, 82f, Color.argb(215, 19, 25, 39))
        rect(c, 16f, 16f, 236f, 74f, Color.argb(170, 54, 69, 94))
        text(c, "TINY QUEST", 27f, 38f, 18f, Color.rgb(255, 224, 127))
        text(c, "HP", 27f, 57f, 10f)
        rect(c, 51f, 49f, 125f, 9f, Color.rgb(67, 38, 50))
        rect(c, 51f, 49f, 125f * hp / 100f, 9f, Color.rgb(235, 75, 80))
        text(c, "MP", 27f, 77f, 10f)
        rect(c, 51f, 68f, 125f, 8f, Color.rgb(39, 48, 72))
        rect(c, 51f, 68f, 125f * mana / 100f, 8f, Color.rgb(66, 185, 245))
        text(c, "★ $score", 188f, 57f, 12f, Color.rgb(255, 215, 95))
        text(c, "¢ $coins", 188f, 77f, 12f, Color.rgb(255, 215, 95))
        // Stage marker
        text(c, listOf("GREEN WILDS", "MOON CAVES", "DARK KEEP")[stage], w - 160f, 28f, 12f, Color.WHITE)
        if (messageTimer > 0f) {
            val tw = paint.apply { textSize = 13f }.measureText(message)
            rect(c, w / 2f - tw / 2f - 15f, 42f, tw + 30f, 26f, Color.argb(185, 20, 26, 39))
            text(c, message, w / 2f - tw / 2f, 60f, 13f, Color.rgb(255, 235, 171))
        }
        // Touch controls
        val jy = h - 94f
        paint.color = Color.argb(110, 12, 20, 35); c.drawCircle(94f, jy, 58f, paint)
        paint.color = Color.argb(210, 222, 231, 244); c.drawCircle(94f + joy * 32f, jy, 25f, paint)
        drawButton(c, w - 205f, h - 78f, "JUMP", Color.rgb(72, 134, 206))
        drawButton(c, w - 103f, h - 78f, "HIT", Color.rgb(195, 75, 83))
        drawButton(c, w - 103f, h - 178f, "MAGIC", Color.rgb(111, 78, 190))
        if (gameOver || won) {
            rect(c, 0f, 0f, w, h, Color.argb(220, 8, 12, 24))
            text(c, if (won) "WORLD CLEARED!" else "GAME OVER", w * .34f, h * .43f, 31f, Color.rgb(255, 221, 128))
            text(c, "TAP ANYWHERE TO PLAY AGAIN", w * .29f, h * .55f, 17f)
            text(c, "SCORE $score    COINS $coins", w * .38f, h * .64f, 14f, Color.rgb(151, 218, 255))
        }
    }

    private fun drawButton(c: Canvas, x: Float, y: Float, label: String, col: Int) {
        paint.color = Color.argb(125, 10, 15, 28); c.drawCircle(x, y, 42f, paint)
        paint.color = col; c.drawCircle(x, y, 34f, paint)
        rect(c, x - 22f, y - 4f, 44f, 2f, Color.argb(75, 255, 255, 255))
        val size = if (label == "MAGIC") 10f else 12f
        paint.textSize = size
        val tw = paint.measureText(label)
        text(c, label, x - tw / 2f, y + 5f, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if ((gameOver || won) && event.actionMasked == MotionEvent.ACTION_DOWN) { reset(); invalidate(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                joy = 0f
                for (i in 0 until event.pointerCount) {
                    val x = event.getX(i); val y = event.getY(i)
                    if (x < width * .34f && y > height * .55f) joy = ((x - 94f) / 54f).coerceIn(-1f, 1f)
                }
                if (event.actionMasked != MotionEvent.ACTION_MOVE) {
                    val x = event.getX(event.actionIndex); val y = event.getY(event.actionIndex)
                    if (x > width - 155f && y > height - 230f && y < height - 120f) skill()
                    else if (x > width - 155f && y > height - 125f) attack()
                    else if (x > width - 255f && x < width - 150f && y > height - 125f) jump()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                joy = 0f
                for (i in 0 until event.pointerCount) {
                    if (i == event.actionIndex) continue
                    val x = event.getX(i); val y = event.getY(i)
                    if (x < width * .34f && y > height * .55f) joy = ((x - 94f) / 54f).coerceIn(-1f, 1f)
                }
            }
        }
        invalidate()
        return true
    }
}
