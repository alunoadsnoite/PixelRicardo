package com.pixelricardo.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TILE = 16

class GameView(private val ctx: Context) : View(ctx) {

    private enum class Scr { TITLE, WORLD, DEX, ACH, SHOP }

    // ---- Cores ---- //
    private val RGBA_TEXT = 0xFFF2F4FA.toInt()
    private val RGBA_GOLD = 0xFFF5C542.toInt()
    private val RGBA_ORANGE = 0xFFF3903F.toInt()
    private val RGBA_GREEN = 0xFF7FD06A.toInt()
    private val RGBA_RED = 0xFFE2605A.toInt()
    private val RGBA_GRAY = 0xFF8A929E.toInt()
    private val RGBA_BUTTON = 0xFF3E4C6B.toInt()
    private val RGBA_BUTTON_HOT = 0xFF4F6B9E.toInt()
    private val RGBA_NAV = 0xCC1A2231.toInt()
    private val RGBA_HUD = 0xCC202938.toInt()

    // ---- Tile constants ---- //
    private object Tile {
        const val GRASS = 0
        const val TALL = 1
        const val FLOWER = 2
        const val WATER = 3
        const val TREE = 4
        const val ROCK = 5
        const val PATH = 6
    }

    // ---- Estado ---- //
    private lateinit var save: SaveData
    private var scr = Scr.TITLE
    private var titleBlink = 0f

    // mundo
    private var map: Array<IntArray> = arrayOf()
    private val MAP_W = 26
    private val MAP_H = 18
    private var px = 13f
    private var py = 9f
    private var targetX: Int? = null
    private var targetY: Int? = null
    private var faceRight = true
    private var walkTimer = 0f
    private var camX = 0f
    private var camY = 0f
    private var lastTileKey = ""
    private var globalTime = 0f

    // criaturas no mundo
    private data class Mob(
        val speciesId: Int,
        var tx: Int,
        var ty: Int,
        val rare: Boolean,
        var bob: Float,
        var moveTimer: Float = 0f,
        var alive: Boolean = true,
        var fleeT: Float = 0f,
        var fleeDx: Int = 0
    )
    private val mobs = ArrayList<Mob>()
    private var respawnTimer = 0f

    // captura
    private data class CatchState(var mob: Mob? = null, var t: Float = 0f, var zone: Float = 0f)
    private val catch = CatchState()

    // seleção
    private var selectedSpecies: Int? = null
    private var evoFlash = 0f
    private var evoSpecies: Int? = null

    // efeitos
    private class FloatText(val text: String, var x: Float, var y: Float, val color: Int, var ttl: Float, val world: Boolean)
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var life: Float)
    private val floaties = ArrayList<FloatText>()
    private val banners = ArrayDeque<String>()
    private var bannerTtl = 0f
    private val particles = ArrayList<Particle>()

    // combos & sessão
    private var catchStreak = 0
    private var sessionCatch = 0
    private var nearMissFlash = 0f

    // missão diária
    private var dailyQuest: QuestDef? = null

    // brilhinhos coletáveis no mapa
    private val collectibles = HashMap<Pair<Int, Int>, Float>()
    private var collectTimer = 2f

    // scroll de conquistas
    private var achScroll = 0f

    // ---- Layout ---- //
    private var vw = 320f
    private var vh = 240f
    private var scale = 1f

    // ---- Arte cacheada ---- //
    private val tileBmp = HashMap<Int, Bitmap>()
    private val grassVariants = mutableListOf<Bitmap>()
    private val flowerVariants = mutableListOf<Bitmap>()
    private val creatureCache = HashMap<Int, Bitmap>()
    private var worldBmp: Bitmap? = null
    private var playerFrames: List<Bitmap> = PlayerArt.FALLBACK
    private var bgPaint: Paint? = null

    // ---- Paint ---- //
    private val pRect = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = RGBA_TEXT
        textSize = 8f
    }
    private val pTextSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = RGBA_TEXT
        textSize = 7f
    }
    private val pTextBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = RGBA_TEXT
        textSize = 20f
    }

    private var attached = false
    private var music: MusicBox? = null
    private val rand = Random(System.nanoTime())

    // ---- Loop ---- //
    private var lastFrame = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!attached) return
            val dt = if (lastFrame == 0L) 1f / 60f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).toFloat()
            lastFrame = frameTimeNanos
            update(dt.coerceIn(0f, 0.05f))
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        attached = false
        persist()
        music?.stop()
        music = null
        SfxBank.release()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = w / 320f
        vw = 320f
        vh = (h / scale).coerceAtLeast(240f)
        clampCamera()
    }

    // ---- Init ---- //
    init {
        isFocusable = true
        loadAll()
    }

    private fun loadAll() {
        save = SaveIO.load(ctx)
        val today = todayStr()
        SfxBank.muted = !save.soundOn
        if (save.soundOn && music == null) {
            music = MusicBox().also { it.start() }
        }
        if (save.lastDay != today) {
            if (save.lastDay.isEmpty()) {
                save.streak = 1
            } else {
                try {
                    val last = java.time.LocalDate.parse(save.lastDay)
                    val diff = java.time.temporal.ChronoUnit.DAYS.between(last, java.time.LocalDate.now()).toInt()
                    save.streak = if (diff == 1) save.streak + 1 else if (diff <= 0) save.streak else 1
                } catch (e: Exception) {
                    save.streak = 1
                }
            }
            save.lastDay = today
            persist()
        }
        save.playCount++
        persist()

        // bônus do dia (primeira abertura do dia)
        if (save.lastGiftDay != today) {
            save.lastGiftDay = today
            gainCoins(10)
            banners.addLast("BÔNUS DO DIA! +10 pedrinhas")
            SfxBank.play(Sounds.GIFT)
            confetti(160f, 60f)
        }

        // missão diária
        val todayLD = java.time.LocalDate.now()
        if (save.questDate != todayLD.toString()) {
            save.questDate = todayLD.toString()
            save.questDone = false
            save.questCatch = 0
            save.questCare = 0
            save.questExplore = 0
            save.questCollect = 0
            save.questRare = false
            save.questCombo = 0
            save.questElement.clear()
            persist()
        }
        dailyQuest = if (save.questDate.isNotEmpty()) DailyQuests.forDay(java.time.LocalDate.parse(save.questDate)) else null

        tileBmp[Tile.TALL] = TileArt.tallGrass()
        tileBmp[Tile.WATER] = TileArt.water()
        tileBmp[Tile.TREE] = TileArt.tree()
        tileBmp[Tile.ROCK] = TileArt.rock()
        tileBmp[Tile.PATH] = TileArt.path()
        grassVariants.clear()
        for (v in 0..2) grassVariants.add(TileArt.grass(v))
        flowerVariants.clear()
        for (v in 0..5) flowerVariants.add(TileArt.flower(v))
        playerFrames = PlayerArt.render(Skins.byId[save.skin] ?: Skins.LIST[0])

        buildWorld()
        renderWorld()
        refreshMobs(forceAll = true)
        lastTileKey = tileKey(px.toInt(), py.toInt())
    }

    private fun persist() {
        if (::save.isInitialized) SaveIO.persist(ctx, save)
    }

    fun persistNow() = persist()

    // ---- Mundo ---- //
    private fun isBlocked(x: Int, y: Int): Boolean {
        if (x < 1 || x >= MAP_W - 1 || y < 1 || y >= MAP_H - 1) return true
        val t = map[y][x]
        return t == Tile.WATER || t == Tile.TREE || t == Tile.ROCK
    }

    private fun buildWorld() {
        map = Array(MAP_H) { IntArray(MAP_W) { Tile.GRASS } }
        for (x in 0 until MAP_W) {
            map[0][x] = Tile.TREE
            map[MAP_H - 1][x] = Tile.TREE
        }
        for (y in 0 until MAP_H) {
            map[y][0] = Tile.TREE
            map[y][MAP_W - 1] = Tile.TREE
        }
        for (y in 3..6) for (x in 4..7) map[y][x] = Tile.WATER
        for (y in 12..14) for (x in 19..21) map[y][x] = Tile.WATER
        for (x in 1 until MAP_W - 1) map[9][x] = Tile.PATH
        for (y in 2 until MAP_H - 1) if (map[y][13] != Tile.WATER) map[y][13] = Tile.PATH

        val r = Random(20260921)
        for (i in 0..160) {
            val x = r.nextInt(2, MAP_W - 2)
            val y = r.nextInt(2, MAP_H - 2)
            if (map[y][x] == Tile.PATH) continue
            val roll = r.nextInt(100)
            map[y][x] = when {
                roll < 20 -> Tile.TREE
                roll < 30 -> Tile.ROCK
                roll < 60 -> Tile.FLOWER
                else -> Tile.TALL
            }
        }
        map[9][13] = Tile.PATH
        spawnCollectibles()
    }

    private fun spawnCollectibles() {
        collectibles.clear()
        var placed = 0
        var tries = 0
        while (placed < 8 && tries < 250) {
            tries++
            val x = 1 + rand.nextInt(MAP_W - 2)
            val y = 1 + rand.nextInt(MAP_H - 2)
            if (isBlocked(x, y)) continue
            if (map[y][x] == Tile.PATH) continue
            if (abs(x - px.toInt()) < 3 && abs(y - py.toInt()) < 3) continue
            if (collectibles.containsKey(x to y)) continue
            collectibles[x to y] = randF() * 6.28f
            placed++
        }
    }

    private fun addCollectible() {
        var tries = 0
        while (tries < 60) {
            tries++
            val x = 1 + rand.nextInt(MAP_W - 2)
            val y = 1 + rand.nextInt(MAP_H - 2)
            if (isBlocked(x, y)) continue
            if (map[y][x] == Tile.PATH) continue
            if (collectibles.containsKey(x to y)) continue
            collectibles[x to y] = randF() * 6.28f
            return
        }
    }

    private fun renderWorld() {
        val b = Bitmap.createBitmap(MAP_W * TILE, MAP_H * TILE, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        for (y in 0 until MAP_H) for (x in 0 until MAP_W) {
            val t = map[y][x]
            val bmp = when (t) {
                Tile.GRASS -> grassVariants[(x + y).rem(grassVariants.size)]
                Tile.FLOWER -> flowerVariants[(x * 3 + y * 7).rem(flowerVariants.size)]
                else -> tileBmp[t]
            }
            bmp?.let { c.drawBitmap(it, (x * TILE).toFloat(), (y * TILE).toFloat(), null) }
        }
        worldBmp = b
    }

    private fun randF(): Float = Random(System.nanoTime()).nextFloat()

    private fun tileKey(x: Int, y: Int) = "$x,$y"

    private fun refreshMobs(forceAll: Boolean, dt: Float = 0f) {
        if (forceAll || mobs.isEmpty()) {
            mobs.clear()
            while (mobs.size < diffCount()) {
                val sp = Catalog.randomId()
                var placed = false
                var tries = 0
                while (!placed && tries < 60) {
                    tries++
                    val x = 2 + rand.nextInt(MAP_W - 4)
                    val y = 2 + rand.nextInt(MAP_H - 4)
                    if (isBlocked(x, y)) continue
                    if (abs(x - px.toInt()) < 4 && abs(y - py.toInt()) < 4) continue
                    if (mobs.any { it.tx == x && it.ty == y }) continue
                    mobs.add(Mob(sp, x, y, randF() < 0.15f, bob = randF() * 8f))
                    placed = true
                }
            }
        } else {
            respawnTimer -= dt
            mobs.removeAll { !it.alive }
            if (respawnTimer <= 0f) {
                if (mobs.size < diffCount()) {
                    mobs.add(spawnMob())
                }
                respawnTimer = 6f
            }
        }
    }

    private fun spawnMob(announceRare: Boolean = true): Mob {
        val sp = Catalog.randomId()
        var m = Mob(sp, 13, 9, false, bob = 0f)
        var tries = 0
        while (tries < 80) {
            tries++
            val x = 2 + rand.nextInt(MAP_W - 4)
            val y = 2 + rand.nextInt(MAP_H - 4)
            if (isBlocked(x, y)) continue
            if (abs(x - px.toInt()) < 4 && abs(y - py.toInt()) < 4) continue
            if (mobs.any { it.tx == x && it.ty == y }) continue
            m = Mob(sp, x, y, randF() < 0.10f, bob = 0f)
            break
        }
        if (m.rare && announceRare) {
            banners.addLast("✨ Uma dourada brilhou no mapa!")
            SfxBank.play(Sounds.RARE)
            confetti(m.tx * TILE + 8f, m.ty * TILE + 8f)
        }
        return m
    }

    // ---- Conquistas ---- //
    private fun checkAchievements() {
        var changed = false
        for (a in Achievements.DEFS) {
            if (a.id in save.unlockedAch) continue
            if (Achievements.progressFor(save, a) >= a.target) {
                save.unlockedAch.add(a.id)
                gainCoins(a.reward)
                banners.addLast("CONQUISTA: ${a.name} (+${a.reward})")
                SfxBank.play(Sounds.QUEST)
                confetti(160f, 90f)
                changed = true
            }
        }
        if (changed) persist()
    }

    private fun gainCoins(n: Int) {
        save.coins += n
        save.coinsEarned += n
    }

    private fun questProgress(q: QuestDef): Pair<Int, Int> {
        val cur = when (q.kind) {
            QuestKind.CATCH -> save.questCatch
            QuestKind.CARE -> save.questCare
            QuestKind.EXPLORE -> save.questExplore
            QuestKind.COLLECT -> save.questCollect
            QuestKind.RARE -> if (save.questRare) 1 else 0
            QuestKind.COMBO -> save.questCombo.coerceAtMost(q.target)
            QuestKind.ELEMENT -> save.questElement[q.element] ?: 0
        }
        return cur to q.target
    }

    private fun checkQuest() {
        val q = dailyQuest ?: return
        if (save.questDone) return
        if (questProgress(q).first >= q.target) {
            save.questDone = true
            gainCoins(q.reward)
            banners.addLast("MISSÃO DO DIA CONCLUÍDA! (+${q.reward})")
            SfxBank.play(Sounds.QUEST)
            confetti(160f, 90f)
            checkAchievements()
            persist()
        }
    }

    private fun confetti(cx: Float, cy: Float) {
        for (i in 0 until 14) {
            val a = i * 0.45f + randF()
            particles.add(Particle(cx, cy, kotlin.math.cos(a) * 16f, kotlin.math.sin(a) * 16f - 8f, listOf(RGBA_GOLD, RGBA_GREEN, 0xFF9BB3FF.toInt(), 0xFFFF9F6B.toInt())[i % 4], 0.9f))
        }
    }

    private fun vibrate(ms: Long) {
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
        }
    }

    private fun toggleSound() {
        save.soundOn = !save.soundOn
        SfxBank.muted = !save.soundOn
        if (save.soundOn) {
            if (music == null) music = MusicBox().also { it.start() } else music?.start()
        } else {
            music?.stop()
        }
        persist()
    }

    private fun comboMult(): Float = when {
        catchStreak >= 4 -> 3f
        catchStreak == 3 -> 2f
        catchStreak == 2 -> 1.5f
        else -> 1f
    }

    private fun comboLabel(): String = when {
        catchStreak >= 4 -> "x3"
        catchStreak == 3 -> "x2"
        catchStreak == 2 -> "x1.5"
        else -> ""
    }

    // ---- Dificuldade ---- //
    private fun diffTol(): Float = listOf(0.13f, 0.09f, 0.06f)[save.difficulty.coerceIn(0, 2)]
    private fun diffPeriod(): Float = listOf(3.2f, 2.5f, 2.0f)[save.difficulty.coerceIn(0, 2)]
    private fun diffCount(): Int = listOf(8, 6, 5)[save.difficulty.coerceIn(0, 2)]
    private fun diffCatchCoins(rare: Boolean): Int {
        val base = if (save.difficulty == 0) 2 else 1
        return base * (if (rare) 3 else 1)
    }

    private fun setDifficulty(d: Int) {
        save.difficulty = d.coerceIn(0, 2)
        persist()
    }

    // ---- Captura ---- //
    private fun startCatch(m: Mob) {
        catch.mob = m
        catch.t = 0f
        catch.zone = 0.25f + randF() * 0.5f
        targetX = null
        targetY = null
    }

    private fun needlePos(t: Float, period: Float = 2.5f): Float {
        val v = (t % period) / period
        return if (v < 0.5f) v * 2f else (1f - v) * 2f
    }

    private fun finishCatch() {
        val m = catch.mob ?: return
        val needle = needlePos(catch.t, diffPeriod())
        val hit = abs(needle - catch.zone) <= diffTol()
        if (hit) {
            catchStreak++
            val combo = comboMult()
            val coins = (diffCatchCoins(m.rare) * combo).toInt()
            m.alive = false
            save.catchTotal++
            if (!save.caught.containsKey(m.speciesId)) {
                save.caught[m.speciesId] = CreatureState()
            }
            if (m.rare && !save.caughtRare) save.caughtRare = true
            gainCoins(coins)
            val cx = m.tx * TILE + 8f
            val cy = m.ty * TILE + 8f
            for (i in 0 until 12) {
                val a = i * 0.523f
                particles.add(Particle(cx, cy, kotlin.math.cos(a) * 10f, kotlin.math.sin(a) * 10f - 6f, RGBA_GOLD, 0.6f))
            }
            floaties.add(FloatText("+$coins pedrinhas!", (m.tx * TILE + 8).toFloat(), (m.ty * TILE).toFloat(), RGBA_GOLD, 1.3f, world = true))
            floaties.add(FloatText("Capturada: ${Catalog.byId[m.speciesId]?.name}!", 20f, 100f, RGBA_GREEN, 1.8f, world = false))

            // primeira captura do dia
            val today = todayStr()
            if (save.lastCatchDay != today) {
                save.lastCatchDay = today
                gainCoins(5)
                floaties.add(FloatText("Primeira captura do dia! +5", 30f, 130f, RGBA_GOLD, 1.6f, world = false))
            }

            // bônus a cada 5 capturas na sessão
            sessionCatch++
            if (sessionCatch > 0 && sessionCatch % 5 == 0) {
                gainCoins(5)
                banners.addLast("$sessionCatch capturas! +5 de bônus")
            }

            // missão diária
            val q = dailyQuest
            if (q != null && !save.questDone) {
                when (q.kind) {
                    QuestKind.CATCH -> save.questCatch++
                    QuestKind.RARE -> if (m.rare) save.questRare = true
                    QuestKind.COMBO -> if (save.questCombo < catchStreak) save.questCombo = catchStreak
                    QuestKind.ELEMENT -> {
                        val el = Catalog.byId[m.speciesId]?.element
                        if (el != null && el == q.element) save.questElement[el] = (save.questElement[el] ?: 0) + 1
                    }
                    else -> Unit
                }
            }

            // combo: efeito + som cada vez mais agudo
            if (catchStreak >= 2) {
                SfxBank.combo(catchStreak)
                floaties.add(FloatText("COMBO ${comboLabel()}!", 20f, 118f, RGBA_GOLD, 1.6f, world = false))
            } else {
                SfxBank.play(Sounds.CATCH)
            }
            if (m.rare) confetti(cx, cy)
            checkQuest()
            checkAchievements()
        } else {
            val dist = abs(needle - catch.zone)
            if (dist > diffTol() && dist < diffTol() * 3f) {
                nearMissFlash = 0.5f
                vibrate(30)
                SfxBank.play(Sounds.NEAR)
                floaties.add(FloatText("Quase!", 30f, 130f, RGBA_ORANGE, 1.1f, world = false))
            } else {
                SfxBank.play(Sounds.MISS)
            }
            m.fleeT = 1.0f
            m.fleeDx = if (m.tx > px.toInt()) 1 else -1
            floaties.add(FloatText("Fugiu...", (m.tx * TILE + 8).toFloat() - 12f, (m.ty * TILE).toFloat(), RGBA_RED, 1.3f, world = true))
            catchStreak = 0
        }
        persist()
        catch.mob = null
    }

    // ---- Cuidados / evolução ---- //
    private fun nextCareTarget(stage: Int) = if (stage == 0) 20 else 80

    private fun doCare(spId: Int, amount: Int) {
        val st = save.caught[spId] ?: return
        st.care += amount
        save.careTotal += amount
        if (dailyQuest?.kind == QuestKind.CARE && !save.questDone) save.questCare++
        // recompensa variável: piso garantido + chance de jackpot
        gainCoins(1)
        if (rand.nextInt(100) < 8) {
            gainCoins(10)
            floaties.add(FloatText("+10 surpresa de amor!", 30f, 130f, RGBA_GOLD, 1.8f, world = false))
            SfxBank.play(Sounds.JACKPOT)
            confetti(160f, 130f)
        } else if (rand.nextInt(100) < 40) {
            floaties.add(FloatText("+1 pedrinha de amor!", 30f, 130f, RGBA_GOLD, 1.2f, world = false))
            SfxBank.play(Sounds.COLLECT)
        }
        val target = nextCareTarget(st.stage)
        if (st.stage < 2 && st.care >= target) {
            st.stage++
            st.care -= target
            save.evolveTotal++
            gainCoins(25)
            evoFlash = 1.8f
            evoSpecies = spId
            banners.addLast("${Catalog.byId[spId]!!.name} evoluiu! nivel ${st.stage + 1}")
            SfxBank.play(Sounds.EVOLVE)
            confetti(160f, 110f)
        }
        checkQuest()
        checkAchievements()
        persist()
    }

    // ---- Loja ---- //
    private fun skinPrice(s: SkinDef): Int {
        val first = save.ownedSkins.size <= 1
        return if (first) (s.price / 2).coerceAtLeast(1) else s.price
    }

    private fun buySkin(id: Int) {
        val s = Skins.byId[id] ?: return
        if (id in save.ownedSkins) {
            save.skin = id
            playerFrames = PlayerArt.render(s)
            SfxBank.play(Sounds.COLLECT)
            persist()
        } else {
            val price = skinPrice(s)
            if (save.coins >= price) {
                save.coins -= price
                save.ownedSkins.add(id)
                save.skin = id
                playerFrames = PlayerArt.render(s)
                floaties.add(FloatText("Novo visual: ${s.name}!", 30f, 130f, RGBA_GREEN, 1.6f, world = false))
                SfxBank.play(Sounds.JACKPOT)
                checkAchievements()
                persist()
            } else {
                floaties.add(FloatText("Pedrinhas insuficientes!", 30f, 130f, RGBA_RED, 1.6f, world = false))
                SfxBank.play(Sounds.MISS)
            }
        }
    }

    // ---- Update ---- //
    private fun update(dt: Float) {
        globalTime += dt
        titleBlink += dt

        if (evoFlash > 0f) evoFlash -= dt else evoSpecies = null

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 30f * dt
            if (p.life <= 0f) pit.remove()
        }
        val fit = floaties.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.ttl -= dt
            f.y -= 6f * dt
            if (f.ttl <= 0f) fit.remove()
        }
        if (bannerTtl > 0f) {
            bannerTtl -= dt
            if (bannerTtl <= 0f) banners.removeFirstOrNull()
        } else if (banners.isNotEmpty()) {
            bannerTtl = 2.7f
        }

        if (nearMissFlash > 0f) nearMissFlash -= dt

        collectTimer -= dt
        if (collectTimer <= 0f && collectibles.size < 8) {
            collectTimer = 4f
            addCollectible()
        }

        refreshMobs(forceAll = false, dt = dt)

        if (scr == Scr.WORLD) updateWorld(dt)
    }

    private fun updateWorld(dt: Float) {
        // caminhar até o alvo
        if (targetX != null) {
            val tx = targetX!!
            val ty = targetY!!
            if (tx != px.toInt() || ty != py.toInt()) {
                val dxTile = tx - px.toInt()
                val dyTile = ty - py.toInt()
                var stepX = 0
                var stepY = 0
                if (abs(dxTile) > abs(dyTile)) stepX = dxTile.coerceIn(-1, 1)
                else stepY = dyTile.coerceIn(-1, 1)
                if (stepX != 0 && isBlocked(px.toInt() + stepX, py.toInt())) stepX = 0
                else if (stepX == 0 && stepY != 0 && isBlocked(px.toInt(), py.toInt() + stepY)) {
                    stepY = 0
                    if (abs(dxTile) > abs(dyTile)) {
                        stepX = dxTile.coerceIn(-1, 1)
                        if (isBlocked(px.toInt() + stepX, py.toInt())) stepX = 0
                    }
                }
                if (stepX != 0 || stepY != 0) {
                    if (stepX != 0) faceRight = stepX > 0
                    px += stepX * 40f * dt
                    py += stepY * 40f * dt
                    walkTimer += dt
                } else {
                    targetX = null
                    targetY = null
                }
            } else {
                targetX = null
                targetY = null
            }
        }

        if (catch.mob != null) catch.t += dt

        // câmera
        val targetCx = px * TILE + 8 - 160
        val targetCy = py * TILE + 8 - 40
        camX += (targetCx - camX) * min(1f, dt * 8f)
        camY += (targetCy - camY) * min(1f, dt * 8f)
        clampCamera()

        // exploração
        val k = tileKey(px.toInt(), py.toInt())
        if (k != lastTileKey) {
            lastTileKey = k
            if (save.visited.add(k)) {
                gainCoins(1)
                if (dailyQuest?.kind == QuestKind.EXPLORE && !save.questDone) {
                    save.questExplore++
                    checkQuest()
                }
                checkAchievements()
            }
            // brilhinho coletado ao pisar no tile
            val tileKeyPair = px.toInt() to py.toInt()
            if (collectibles.remove(tileKeyPair) != null) {
                val cc = 2 + rand.nextInt(2)
                gainCoins(cc)
                SfxBank.play(Sounds.COLLECT)
                val ex = tileKeyPair.first * TILE + 8f
                val ey = tileKeyPair.second * TILE + 8f
                confetti(ex, ey)
                floaties.add(FloatText("+$cc pedrinhas!", ex, ey, RGBA_GOLD, 1.4f, world = true))
                if (dailyQuest?.kind == QuestKind.COLLECT && !save.questDone) {
                    save.questCollect++
                    checkQuest()
                }
            }
        }

        // criaturas
        for (m in mobs) {
            if (!m.alive) continue
            m.bob += dt
            if (m.fleeT > 0f) {
                m.fleeT -= dt
                if (m.fleeT <= 0f) {
                    m.alive = false
                } else if (randF() < 0.2f) {
                    val nx = m.tx + m.fleeDx
                    if (!isBlocked(nx, m.ty)) m.tx = nx
                }
                continue
            }
            m.moveTimer -= dt
            if (m.moveTimer <= 0f && catch.mob == null) {
                m.moveTimer = 1.0f + randF() * 1.2f
                val cand = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0).filter { !isBlocked(m.tx + it.first, m.ty + it.second) }
                if (cand.isNotEmpty()) {
                    val d = cand[rand.nextInt(cand.size)]
                    m.tx += d.first
                    m.ty += d.second
                }
            }
        }
    }

    private fun clampCamera() {
        val viewW = 320f
        val viewH = vh - 46f
        val camW = MAP_W * TILE - viewW
        val camH = MAP_H * TILE - viewH
        camX = if (camW > 0) camX.coerceIn(0f, camW) else 0f
        camY = if (camH > 0) camY.coerceIn(0f, camH) else 0f
    }

    // ---- Desenho ---- //
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(scale, scale)

        val midY = (vh - 240f).coerceAtLeast(0f) / 2f

        drawBg(canvas)

        when (scr) {
            Scr.TITLE -> drawTitle(canvas, midY)
            Scr.WORLD -> drawWorld(canvas, midY)
            Scr.DEX -> drawDex(canvas, midY)
            Scr.ACH -> drawAch(canvas, midY)
            Scr.SHOP -> drawShop(canvas, midY)
        }

        drawBanner(canvas, midY)
        drawFloaties(canvas, midY)
        drawParticles(canvas, midY)

        canvas.restore()
    }

    private fun drawBg(c: Canvas) {
        val g = android.graphics.LinearGradient(
            0f, 0f, 0f, vh,
            intArrayOf(0xFF22375C.toInt(), 0xFF45306B.toInt(), 0xFF77395F.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        if (bgPaint == null) bgPaint = Paint().apply { isAntiAlias = false }
        bgPaint!!.shader = g
        c.drawRect(0f, 0f, 320f, vh, bgPaint!!)
        bgPaint!!.shader = null
    }

    private fun drawPanel(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        pRect.color = 0xFF2A3550.toInt()
        c.drawRect(x, y, x + w, y + h, pRect)
        pRect.color = 0xFF56688C.toInt()
        c.drawRect(x, y, x + w, y + 1f, pRect)
        c.drawRect(x, y, x + 1f, y + h, pRect)
        pRect.color = 0xFF141A2B.toInt()
        c.drawRect(x, y + h - 1f, x + w, y + h, pRect)
        c.drawRect(x + w - 1f, y, x + w, y + h, pRect)
    }

    private fun drawSprite(c: Canvas, bmp: Bitmap, x: Float, y: Float, sc: Float = 1f) {
        c.drawBitmap(bmp, null, Rect(
            x.toInt(), y.toInt(),
            (x + 16 * sc).toInt(), (y + 16 * sc).toInt()
        ), null)
    }

    private fun drawTitle(c: Canvas, midY: Float) {
        val dec = listOf(4, 9, 19, 12)
        dec.forEachIndexed { i, id ->
            drawSprite(c, creatureBmp(id, 0, false), 40f + i * 70f, 26f + midY)
        }
        val logo = "PixelRicardo"
        pTextBig.color = RGBA_TEXT
        c.drawText(logo, (320f - pTextBig.measureText(logo)) / 2f, 96f + midY, pTextBig)
        val sub = "Um mundo pixel cheio de amiguinhos"
        pText.color = RGBA_GOLD
        c.drawText(sub, (320f - pText.measureText(sub)) / 2f, 112f + midY, pText)

        drawTrophyIcon(c, 26f, 150f + midY, false)
        pTextSmall.color = RGBA_TEXT
        c.drawText("${save.unlockedAch.size}/${Achievements.DEFS.size}", 38f, 158f + midY, pTextSmall)
        drawCoinIcon(c, 110f, 150f + midY)
        c.drawText("${save.coins}", 122f, 158f + midY, pTextSmall)

        // seletor de dificuldade
        val labels = listOf("FACIL", "MEDIO", "DIFICIL")
        val dy = 168f + midY
        val wBtn = 84f
        labels.forEachIndexed { i, lb ->
            val x = 34f + i * (wBtn + 12f)
            if (save.difficulty == i) {
                pRect.color = RGBA_BUTTON_HOT
                c.drawRect(x, dy, x + wBtn, dy + 18f, pRect)
            } else {
                drawPanel(c, x, dy, wBtn, 18f)
            }
            pTextSmall.color = if (save.difficulty == i) RGBA_GOLD else RGBA_TEXT
            c.drawText(lb, x + (wBtn - pTextSmall.measureText(lb)) / 2f, dy + 13f, pTextSmall)
        }

        if ((titleBlink % 1.1f) < 0.75f) {
            val go = "TOQUE PARA COMEÇAR"
            pText.color = RGBA_GREEN
            c.drawText(go, (320f - pText.measureText(go)) / 2f, 206f + midY, pText)
        }

        // toggle de som
        val sl = "SOM: ${if (save.soundOn) "ON" else "OFF"}"
        drawPanel(c, 260f, 4f, 56f, 16f)
        pTextSmall.color = if (save.soundOn) RGBA_GREEN else RGBA_GRAY
        c.drawText(sl, 268f, 15f, pTextSmall)
    }

    private fun drawCoinIcon(c: Canvas, x: Float, y: Float) {
        pRect.color = RGBA_GOLD
        c.drawRect(x, y, x + 10f, y + 10f, pRect)
        pRect.color = 0xFFE0A31E.toInt()
        c.drawRect(x + 2f, y + 4f, x + 8f, y + 6f, pRect)
    }

    private fun drawTrophyIcon(c: Canvas, x: Float, y: Float, gold: Boolean) {
        pRect.color = if (gold) RGBA_GOLD else RGBA_GRAY
        c.drawRect(x + 2f, y, x + 8f, y + 3f, pRect)
        c.drawRect(x + 3f, y + 3f, x + 7f, y + 8f, pRect)
        c.drawRect(x, y + 5f, x + 2f, y + 8f, pRect)
        c.drawRect(x + 8f, y + 5f, x + 10f, y + 8f, pRect)
        pRect.color = if (gold) 0xFFE0A31E.toInt() else 0xFF555D68.toInt()
        c.drawRect(x + 3f, y + 8f, x + 7f, y + 10f, pRect)
    }

    private fun flameIcon(c: Canvas, x: Float, y: Float) {
        pRect.color = RGBA_ORANGE
        c.drawRect(x + 4f, y, x + 7f, y + 4f, pRect)
        pRect.color = RGBA_GOLD
        c.drawRect(x + 3f, y + 2f, x + 8f, y + 8f, pRect)
        pRect.color = RGBA_RED
        c.drawRect(x + 6f, y + 5f, x + 8f, y + 10f, pRect)
        c.drawRect(x + 2f, y + 6f, x + 4f, y + 10f, pRect)
    }

    private fun drawHud(c: Canvas) {
        pRect.color = RGBA_HUD
        c.drawRect(0f, 0f, 320f, 22f, pRect)
        drawCoinIcon(c, 6f, 6f)
        pText.color = RGBA_TEXT
        c.drawText("${save.coins}", 18f, 15f, pText)
        flameIcon(c, 96f, 5f)
        c.drawText("${save.streak}d", 110f, 15f, pText)
        c.drawText("${save.caught.size}/24", 284f, 15f, pText)
    }

    private fun drawNav(c: Canvas) {
        val navY = vh - 24f
        pRect.color = RGBA_NAV
        c.drawRect(0f, navY, 320f, vh, pRect)
        val names = listOf("Mundo", "Coleta", "Troféus", "Loja")
        val screens = listOf(Scr.WORLD, Scr.DEX, Scr.ACH, Scr.SHOP)
        val wBtn = 80f
        screens.forEachIndexed { i, s ->
            val x = i * wBtn
            if (scr == s) {
                pRect.color = RGBA_BUTTON_HOT
                c.drawRect(x, navY, x + wBtn, navY + 24f, pRect)
            }
            val nm = names[i]
            pText.color = if (scr == s) RGBA_GOLD else RGBA_TEXT
            c.drawText(nm, x + (wBtn - pText.measureText(nm)) / 2f, navY + 15f, pText)
        }
    }

    private fun drawWorld(c: Canvas, midY: Float) {
        val worldOffY = 22f + midY
        val viewW = 320f
        val hTile = vh - 46f
        val srcW = min(viewW, (MAP_W * TILE).toFloat())
        val srcH = min(hTile, (MAP_H * TILE).toFloat())
        val src = Rect(camX.toInt(), camY.toInt(), (camX + srcW).toInt(), (camY + srcH).toInt())
        val dst = Rect(0, worldOffY.toInt(), 320, (worldOffY + srcH).toInt())
        worldBmp?.let { c.drawBitmap(it, src, dst, null) }

        val bobOff = -2f
        for (m in mobs) {
            if (!m.alive) continue
            val sp = Catalog.byId[m.speciesId] ?: continue
            val stage = (save.caught[m.speciesId]?.stage ?: 0).coerceIn(0, 2)
            val wx = m.tx * TILE - camX
            val wy = m.ty * TILE - camY + worldOffY + bobOff + sin(m.bob * 3f) * 1.5f
            pRect.color = 0x44000000
            c.drawRect(wx + 2f, wy + 13f, wx + 15f, wy + 15f, pRect)
            drawSprite(c, creatureBmp(sp.id, stage, m.rare), wx, wy)
            if (m.rare) {
                pRect.color = 0xFFF5C542.toInt()
                c.drawRect(wx, wy - 2f, wx + TILE, wy, pRect)
                pRect.color = 0x33F5C542.toInt()
                c.drawRect(wx, wy + 16f, wx + TILE, wy + 18f, pRect)
            }
        }

        // brilhinhos coletáveis
        for ((tile, phase) in collectibles) {
            val sx = tile.first * TILE + 2f - camX
            val sy = tile.second * TILE - camY + worldOffY + sin(phase + globalTime * 3f) * 1.5f
            val vis = ((globalTime * 3f + phase) % 1f) < 0.8f
            if (!vis) continue
            val pr = 0xFFF5C542.toInt()
            pRect.color = pr
            c.drawRect(sx + 5f, sy, sx + 8f, sy + 3f, pRect)
            c.drawRect(sx, sy + 5f, sx + 3f, sy + 8f, pRect)
            c.drawRect(sx + 10f, sy + 5f, sx + 13f, sy + 8f, pRect)
            c.drawRect(sx + 5f, sy + 10f, sx + 8f, sy + 13f, pRect)
        }

        // player
        val pwx = px * TILE - camX
        val pwy = py * TILE - camY + worldOffY
        pRect.color = 0x44000000
        c.drawRect(pwx + 2f, pwy + 12f, pwx + 15f, pwy + 15f, pRect)
        val bmp = playerFrames[if ((walkTimer % 0.5f) < 0.25f) 0 else 1]
        drawSprite(c, bmp, pwx, pwy - 2f)

        // alvo de caminhada
        if (targetX != null) {
            pRect.color = 0x55FFFFFF
            val ax = targetX!! * TILE - camX + 3f
            val ay = targetY!! * TILE - camY + worldOffY + 3f
            c.drawRect(ax, ay, ax + 10f, ay + 10f, pRect)
        }

        // UI de captura
        if (catch.mob != null) {
            val cx = 160f
            val cy = 30f
            drawPanel(c, cx - 110f, cy, 220f, 30f)
            pText.color = RGBA_TEXT
            val tip = "Toque para parar a agulha!"
            c.drawText(tip, cx - 100f, cy + 14f, pText)
            val n = needlePos(catch.t, diffPeriod())
            val z0 = 50f + catch.zone * 100f
            pRect.color = if (nearMissFlash > 0f) RGBA_ORANGE else RGBA_GREEN
            val flashW = if (nearMissFlash > 0.25f) 32f else 28f
            c.drawRect(cx - 100f + z0, cy + 18f, cx - 100f + z0 + flashW, cy + 20f, pRect)
            pRect.color = RGBA_GOLD
            c.drawRect(cx - 100f + n * 100f, cy + 18f, cx - 100f + n * 100f + 3f, cy + 20f, pRect)
            if (catchStreak >= 2) {
                val cl = "COMBO ${comboLabel()}"
                pText.color = RGBA_GOLD
                c.drawText(cl, (320f - pText.measureText(cl)) / 2f, cy + 26f, pText)
            }
        }

        // missão do dia
        val q = dailyQuest
        if (q != null) {
            val (cur, target) = questProgress(q)
            if (save.questDone) {
                pTextSmall.color = RGBA_GREEN
                val done = "Missão do dia: feita! (+${q.reward})"
                c.drawText(done, 2f, 38f + midY, pTextSmall)
            } else {
                pTextSmall.color = RGBA_GOLD
                val qt = "Missão: ${q.desc} ($cur/$target)"
                c.drawText(qt, 2f, 38f + midY, pTextSmall)
            }
        }

        drawHud(c)
        drawNav(c)
    }

    private fun drawDex(c: Canvas, midY: Float) {
        drawHud(c)
        drawNav(c)
        val sel = selectedSpecies
        if (sel == null) drawDexGrid(c, midY) else drawCare(c, sel, midY)
    }

    private fun drawDexGrid(c: Canvas, midY: Float) {
        pText.color = RGBA_TEXT
        val hd = "MINHA COLETÂNEA"
        c.drawText(hd, (320f - pText.measureText(hd)) / 2f, 34f + midY, pText)
        pTextSmall.color = RGBA_GRAY
        c.drawText("${save.caught.size}/24 capturadas", 110f, 46f + midY, pTextSmall)

        val y0 = 52f + midY
        val cols = 6
        val cellW = 320f / cols
        val cellH = 40f
        Catalog.SPECIES.forEachIndexed { idx, sp ->
            val row = idx / cols
            val col = idx % cols
            val x = col * cellW
            val y = y0 + row * cellH
            val owned = save.caught.containsKey(idx)
            pRect.color = if (owned) 0xFF2A3550.toInt() else 0xFF171F31.toInt()
            c.drawRect(x + 2f, y, x + cellW - 2f, y + cellH - 2f, pRect)
            if (owned) {
                val st = save.caught[idx]!!.stage.coerceIn(0, 2)
                drawSprite(c, creatureBmp(idx, st, false), x + cellW / 2f - 8f, y + 4f)
                pTextSmall.color = RGBA_TEXT
                c.drawText(sp.name, x + 2f, y + cellH - 4f, pTextSmall)
            } else {
                pTextSmall.color = RGBA_GRAY
                c.drawText("?", x + cellW / 2f - 3f, y + cellH / 2f + 3f, pTextSmall)
            }
        }
    }

    private fun drawCare(c: Canvas, spId: Int, midY: Float) {
        val sp = Catalog.byId[spId]!!
        val st = save.caught[spId]!!

        // voltar
        drawPanel(c, 4f, 26f + midY, 18f, 14f)
        pText.color = RGBA_TEXT
        c.drawText("<", 9f, 36f + midY, pText)

        pText.color = RGBA_TEXT
        c.drawText(sp.name, 30f, 36f + midY, pText)
        pTextSmall.color = RGBA_GOLD
        c.drawText("Tipo: ${sp.element}", 30f, 49f + midY, pTextSmall)

        drawSprite(c, creatureBmp(spId, st.stage, false), 60f, 58f + midY, 4f)
        pTextSmall.color = RGBA_TEXT
        c.drawText("nível ${st.stage + 1}", 130f, 84f + midY, pTextSmall)

        // afeto
        val target = nextCareTarget(st.stage)
        val pct = (st.care.toFloat() / target).coerceIn(0f, 1f)
        pRect.color = RGBA_NAV
        c.drawRect(130f, 92f + midY, 310f, 97f + midY, pRect)
        pRect.color = RGBA_GREEN
        c.drawRect(130f, 92f + midY, 130f + pct * 180f, 97f + midY, pRect)
        pTextSmall.color = RGBA_TEXT
        c.drawText(if (st.stage < 2) "${st.care}/$target" else "nivel MAX", 130f, 110f + midY, pTextSmall)
        if (st.stage < 2 && pct >= 1f) {
            pTextSmall.color = RGBA_GOLD
            val ev = "pronta(o) pra evoluir!"
            c.drawText(ev, 190f, 110f + midY, pTextSmall)
        }

        // desc
        pTextSmall.color = RGBA_TEXT
        val words = sp.desc.split(" ")
        var dw = 24f
        var line = 130f
        words.forEach { w ->
            val tw = pTextSmall.measureText(w + " ")
            if (dw + tw > 305f) {
                dw = 24f
                line += 12f
            }
            c.drawText(w, dw, line + midY, pTextSmall)
            dw += tw
        }

        // botões
        val defs = listOf(
            Triple("ALIMENTAR +5", 0xFFFF9F6B.toInt(), 0),
            Triple("BRINCAR +7", 0xFF9BB3FF.toInt(), 1),
            Triple("ESCOVAR +3", 0xFFB4E9A8.toInt(), 2)
        )
        val by = vh - 58f
        defs.forEach { (label, accent, idx) ->
            val bx = 24f + idx * 92f
            drawButton(c, bx, by, 86f, 24f, label, accent)
        }

        drawEvoFlash(c, midY)
    }

    private fun drawButton(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, accent: Int) {
        drawPanel(c, x, y, w, h)
        pRect.color = accent
        c.drawRect(x, y, x + w, y + 2f, pRect)
        pText.color = RGBA_TEXT
        c.drawText(label, x + (w - pText.measureText(label)) / 2f, y + h - 7f, pText)
    }

    private fun drawEvoFlash(c: Canvas, midY: Float) {
        if (evoFlash > 0f && evoSpecies != null) {
            val alpha = if (evoFlash > 1.3f) 200 else (200 * (evoFlash / 1.3f)).toInt()
            pRect.color = (0x00FFF0B8) or (alpha shl 24)
            c.drawRect(0f, 22f + midY, 320f, vh - 24f, pRect)
            val sp = Catalog.byId[evoSpecies!!]!!
            pText.color = RGBA_GOLD
            val m = "${sp.name} EVOLUIU!"
            c.drawText(m, (320f - pText.measureText(m)) / 2f, 96f + midY, pText)
            drawSprite(c, creatureBmp(sp.id, 2, false), 144f, 106f + midY, 3f)
        }
    }

    private fun drawAch(c: Canvas, midY: Float) {
        drawHud(c)
        drawNav(c)
        val hd = "CONQUISTAS"
        pText.color = RGBA_TEXT
        c.drawText(hd, (320f - pText.measureText(hd)) / 2f, 34f + midY, pText)
        pTextSmall.color = RGBA_GRAY
        c.drawText("${save.unlockedAch.size}/${Achievements.DEFS.size}", 286f, 46f + midY, pTextSmall)

        val rowH = 40f
        val yBase = 54f + midY + achScroll
        val topClip = 54f + midY
        val botClip = vh - 24f
        for (a in Achievements.DEFS) {
            val top = yBase + a.id * rowH
            val bot = top + rowH - 2f
            if (bot < topClip || top > botClip) continue
            val unlocked = a.id in save.unlockedAch
            pRect.color = if (unlocked) 0xFF2A3550.toInt() else 0xFF1B2336.toInt()
            c.drawRect(0f, top, 320f, bot, pRect)
            drawTrophyIcon(c, 8f, top + 6f, unlocked)
            pTextSmall.color = if (unlocked) RGBA_GOLD else RGBA_TEXT
            c.drawText(a.name, 26f, top + 11f, pTextSmall)
            pTextSmall.color = RGBA_GRAY
            c.drawText(a.desc, 26f, top + 22f, pTextSmall)
            if (unlocked) {
                pTextSmall.color = RGBA_GREEN
                c.drawText("ok", 296f, top + 11f, pTextSmall)
            } else {
                val prog = Achievements.progressFor(save, a)
                pTextSmall.color = RGBA_TEXT
                c.drawText("$prog/${a.target}", 270f, top + 11f, pTextSmall)
                pRect.color = RGBA_NAV
                c.drawRect(26f, top + 28f, 314f, top + 31f, pRect)
                pRect.color = RGBA_GOLD
                val fw = 288f * (prog.toFloat() / a.target.toFloat())
                c.drawRect(26f, top + 28f, 26f + fw, top + 31f, pRect)
            }
        }
    }

    private fun drawShop(c: Canvas, midY: Float) {
        drawHud(c)
        drawNav(c)
        val hd = "LOJA DE VISUAIS"
        pText.color = RGBA_TEXT
        c.drawText(hd, (320f - pText.measureText(hd)) / 2f, 34f + midY, pText)
        drawCoinIcon(c, 250f, 28f + midY)
        pText.color = RGBA_TEXT
        c.drawText("${save.coins}", 262f, 36f + midY, pText)

        val cols = 3
        val cellW = 106f
        val cellH = 62f
        Skins.LIST.forEachIndexed { i, s ->
            val row = i / cols
            val col = i % cols
            val x = col * cellW
            val y = 46f + row * cellH + midY
            drawPanel(c, x + 2f, y, cellW - 4f, cellH - 6f)
            val owned = s.id in save.ownedSkins
            val current = save.skin == s.id
            val prevBmp = playerFrameBmp(s)
            c.drawBitmap(prevBmp, null, Rect(
                (x + cellW / 2f - 18f).toInt(), (y + 6f).toInt(),
                (x + cellW / 2f + 18f).toInt(), (y + 24f).toInt()
            ), null)
            pTextSmall.color = RGBA_TEXT
            c.drawText(s.name, x + cellW / 2f - pTextSmall.measureText(s.name) / 2f, y + 34f, pTextSmall)
            if (current) {
                pTextSmall.color = RGBA_GREEN
                c.drawText("USANDO", x + cellW / 2f - 18f, y + 50f, pTextSmall)
            } else if (owned) {
                pTextSmall.color = RGBA_GOLD
                c.drawText("USAR", x + cellW / 2f - 10f, y + 50f, pTextSmall)
            } else {
                val price = skinPrice(s)
                val first = save.ownedSkins.size <= 1
                if (first && price < s.price) {
                    pTextSmall.color = RGBA_ORANGE
                    c.drawText("-50%", x + 2f, y + 22f, pTextSmall)
                }
                drawCoinIcon(c, x + 20f, y + 43f)
                pTextSmall.color = RGBA_TEXT
                c.drawText("$price", x + 40f, y + 51f, pTextSmall)
            }
        }
    }

    private val memoPlayer = HashMap<Int, Bitmap>()
    private fun playerFrameBmp(s: SkinDef): Bitmap =
        memoPlayer.getOrPut(s.id) { PlayerArt.render(s)[0] }

    private fun drawParticles(c: Canvas, midY: Float) {
        for (p in particles) {
            pRect.color = p.color
            c.drawRect(p.x - camX, p.y - camY + 22f + midY, p.x - camX + 2f, p.y - camY + 24f + midY, pRect)
        }
    }

    private fun drawFloaties(c: Canvas, midY: Float) {
        for (f in floaties) {
            pText.color = f.color
            if (f.world) {
                c.drawText(f.text, f.x - camX, f.y - camY + 22f + midY, pText)
            } else {
                c.drawText(f.text, f.x, f.y + midY, pText)
            }
        }
    }

    private fun drawBanner(c: Canvas, midY: Float) {
        if (bannerTtl > 0f && banners.isNotEmpty()) {
            val txt = banners.first()
            val w = pText.measureText(txt) + 18f
            val y = 70f + midY
            drawPanel(c, (320f - w) / 2f, y, w, 22f)
            pText.color = RGBA_GOLD
            c.drawText(txt, (320f - pText.measureText(txt)) / 2f, y + 16f, pText)
        }
    }

    private fun creatureBmp(spId: Int, stage: Int, rare: Boolean): Bitmap {
        val key = spId * 8 + stage * 2 + (if (rare) 1 else 0)
        return creatureCache.getOrPut(key) {
            CreatureArt.render(Catalog.byId[spId]!!, stage, rare)
        }
    }

    // ---- Toque ---- //
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTap(event.x / scale, event.y / scale)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scr == Scr.ACH) {
                    if (event.historySize > 0) {
                        val prev = event.getHistoricalY(event.historySize - 1) / scale
                        val cur = event.y / scale
                        achScroll += (cur - prev) * 0.8f
                        val max = Achievements.DEFS.size * 40f - (vh - 80f)
                        achScroll = if (max > 0) achScroll.coerceIn(-max, 0f) else 0f
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onTap(x: Float, y: Float) {
        if (bannerTtl > 0f) {
            bannerTtl = 0f
            banners.removeFirstOrNull()
            return
        }
        if (scr == Scr.TITLE) {
            val midY = (vh - 240f).coerceAtLeast(0f) / 2f
            if (y in 4f..20f && x in 260f..316f) {
                toggleSound()
                return
            }
            if (y in (168f + midY)..(186f + midY) && x in 34f..(34f + 3 * 96f)) {
                val dIdx = ((x - 34f) / 96f).toInt().coerceIn(0, 2)
                if ((x - 34f) % 96f < 84f) setDifficulty(dIdx)
                return
            }
            scr = Scr.WORLD
            return
        }

        val navY = vh - 24f
        if (y >= navY) {
            selectedSpecies = null
            val idx = (x / 80f).toInt().coerceIn(0, 3)
            scr = when (idx) {
                0 -> Scr.WORLD
                1 -> Scr.DEX
                2 -> Scr.ACH
                else -> Scr.SHOP
            }
            return
        }

        if (scr == Scr.WORLD && catch.mob != null) {
            finishCatch()
            return
        }

        when (scr) {
            Scr.WORLD -> {
                // criatura?
                val midY = (vh - 240f).coerceAtLeast(0f) / 2f
                val wx = camX + x
                val wy = camY + (y - 22f - midY)
                val m = mobs.firstOrNull { mob ->
                    mob.alive && mob.fleeT <= 0f &&
                        abs((mob.tx * TILE + 8) - wx) < 11f &&
                        abs((mob.ty * TILE + 8) - wy) < 11f
                }
                if (m != null) {
                    startCatch(m)
                } else {
                    val tx = (wx / TILE).toInt()
                    val ty = (wy / TILE).toInt()
                    if (ty in 0 until MAP_H) {
                        targetX = tx
                        targetY = ty
                    }
                }
            }
            Scr.DEX -> {
                val sel = selectedSpecies
                if (sel != null) {
                    val midY = (vh - 240f).coerceAtLeast(0f) / 2f
                    if (x < 24f && y > (24f + midY) && y < (46f + midY)) {
                        selectedSpecies = null
                        return
                    }
                    val by = vh - 58f
                    if (y in by..(by + 24f)) {
                        val idx = ((x - 24f) / 92f).toInt().coerceIn(0, 2)
                        when (idx) {
                            0 -> doCare(sel, 5)
                            1 -> doCare(sel, 7)
                            2 -> doCare(sel, 3)
                        }
                    }
                } else {
                    val midY = (vh - 240f).coerceAtLeast(0f) / 2f
                    val col = (x / (320f / 6f)).toInt()
                    val row = ((y - 52f - midY) / 40f).toInt()
                    val idx = row * 6 + col
                    if (idx in 0 until Catalog.NUM_SPECIES) {
                        if (save.caught.containsKey(idx)) {
                            selectedSpecies = idx
                        } else {
                            floaties.add(FloatText("Ainda não capturada!", 60f, 130f, RGBA_GRAY, 1.3f, world = false))
                        }
                    }
                }
            }
            Scr.ACH -> Unit
            Scr.SHOP -> {
                val midY = (vh - 240f).coerceAtLeast(0f) / 2f
                val col = (x / (320f / 3f)).toInt()
                val row = ((y - 46f - midY) / 62f).toInt()
                val idx = row * 3 + col
                if (idx in Skins.LIST.indices) buySkin(idx)
            }
            Scr.TITLE -> Unit
        }
    }
}