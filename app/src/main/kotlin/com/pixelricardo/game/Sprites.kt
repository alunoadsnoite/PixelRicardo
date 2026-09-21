package com.pixelricardo.game

import android.graphics.Bitmap

// Gera Bitmaps pixel-art em 16x16 sem usar assets de imagem.
// Cada personagem utiliza uma tabela de padrões (strings) e um mapa de paleta.

fun rgb(r: Int, g: Int, b: Int): Int = (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b

fun hsl2rgb(h0: Float, s: Float, l: Float): Int {
    var h = h0 % 1f
    if (h < 0f) h += 1f
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun hue2rgb(pp: Float, qq: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        if (t < 1f / 6f) return pp + (qq - pp) * 6f * t
        if (t < 1f / 2f) return qq
        if (t < 2f / 3f) return pp + (qq - pp) * (2f / 3f - t) * 6f
        return pp
    }
    val r = hue2rgb(p, q, h + 1f / 3f)
    val g = hue2rgb(p, q, h)
    val b = hue2rgb(p, q, h - 1f / 3f)
    return 0xFF000000.toInt() or ((r * 255f).toInt() shl 16) or ((g * 255f).toInt() shl 8) or (b * 255f).toInt()
}

class Canvas2D(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun set(x: Int, y: Int, c: Int) {
        if (x in 0 until w && y in 0 until h) px[y * w + x] = c
    }

    fun at(x: Int, y: Int): Int =
        if (x in 0 until w && y in 0 until h) px[y * w + x] else 0

    fun toBitmap(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }
}

private fun parse(pattern: List<String>, palette: Map<Char, Int>): Canvas2D {
    val h0 = pattern.size
    val w0 = pattern[0].length
    val c = Canvas2D(w0, h0)
    pattern.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            palette[ch]?.let { c.set(x, y, it) }
        }
    }
    return c
}

private fun trim16(c: Canvas2D): Canvas2D {
    // Encaixa a arte num canvas 16x6 transparente (usado apenas p/ largura padrão).
    val out = Canvas2D(16, c.h)
    val ox = (16 - c.w) / 2
    for (y in 0 until c.h) for (x in 0 until c.w) {
        val col = c.at(x, y)
        if (col != 0 && x + ox in 0 until 16) out.px[y * 16 + x + ox] = col
    }
    return out
}

object CreatureArt {
    const val SIZE = 16

    private val STAGE0 = listOf(
        "..oooooooooooo..",
        ".oBBBBBBBBBBBBo.",
        "oBBBBLBBBBBBBBBo",
        "oBBBBLBBBBBBBBBo",
        "oBBWWBBBBBBWWBBo",
        "oBPpBBBBBBBBPpBo",
        "oBBBBMMMMMBBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBDBBBBBBBBBBDBo",
        ".oBBDDDDDDDDDBo.",
        "..oooooooooooo..",
    )

    private val STAGE1 = listOf(
        "..oooooooooooo..",
        ".oBBBBBBBBBBBBo.",
        "oBBBBBBBBBBBBBBo",
        "oBBLBBBBBBBBBBBo",
        "oBBLLBBBBBBBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBBWWBBBBBBWWBBo",
        "oBBPPBBBBBBPPBBo",
        "oBBBBMMMMMMBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBDBBBBBBBBBBDBo",
        ".oBBDDDDDDDDDBo.",
        "..oooooooooooo..",
    )

    private val STAGE2 = listOf(
        "oo............oo",
        "oEooooooooooooEo",
        ".oBBBBBBBBBBBBo.",
        "oBBBBBBBBBBBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBBLLBBBBBBLLBBo",
        "oBBLLBBBBBBLLBBo",
        "oBBWWBBBBBBWWBBo",
        "oBBPPBBBBBBPPBBo",
        "oBBBBMMMMMMBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBBBBBBBBBBBBBBo",
        "oBDBBBBBBBBBBDBo",
        "oBBDDDDDDDDDDDBo",
        ".oBBBBBBBBBBBBo.",
        "..oooooooooooo..",
    )

    private val PATTERNS = listOf(STAGE0, STAGE1, STAGE2)

    fun palette(hue: Float, rare: Boolean): Map<Char, Int> {
        val base = if (rare) 0.13f else hue
        return mapOf(
            'o' to 0xFF26303F.toInt(),
            'B' to hsl2rgb(base, 0.62f, 0.58f),
            'L' to hsl2rgb(base, 0.70f, 0.74f),
            'D' to hsl2rgb(base, 0.55f, 0.36f),
            'W' to 0xFFF4F6FF.toInt(),
            'P' to 0xFF1B2233.toInt(),
            'p' to 0xFFF4F6FF.toInt(),
            'M' to 0xFFE2605A.toInt(),
            'E' to if (rare) 0xFFFFD54A.toInt() else 0xFF3E2B23.toInt(),
        )
    }

    fun render(species: Species, stage: Int, rare: Boolean): Bitmap {
        val st = stage.coerceIn(0, 2)
        val c = parse(PATTERNS[st], palette(species.baseHue, rare))
        return c.toBitmap()
    }

    fun sprite(w: Int, h: Int, pattern: List<String>, pal: Map<Char, Int>): Bitmap {
        val c = parse(pattern, pal)
        return trim16(c).toBitmap()
    }
}

object TileArt {
    const val SIZE = 16

    private val grassBase = listOf(0xFF57B54A.toInt(), 0xFF5FBE4E.toInt(), 0xFF52AC42.toInt())
    private val grassDark = listOf(0xFF3E9C48.toInt(), 0xFF44A347.toInt(), 0xFF37943E.toInt())
    private val grassColLight = 0xFF7FD06A.toInt()
    private val waterDark = 0xFF2E7FC4.toInt()
    private val waterBase = 0xFF3FA0D9.toInt()
    private val waterLight = 0xFFB7E6FF.toInt()
    private val flowerColors = listOf(
        0xFFFFFFFF.toInt(), 0xFFFF8FC2.toInt(), 0xFFFF6B5E.toInt(),
        0xFFFBE05A.toInt(), 0xFFB79CFF.toInt(), 0xFFFFAA55.toInt()
    )

    fun grass(variant: Int = 0): Bitmap {
        val v = variant.rem(grassBase.size)
        val base = grassBase[v]
        val dark = grassDark[v]
        val c = Canvas2D(SIZE, SIZE)
        c.px.fill(base)
        val dots = arrayOf(
            intArrayOf(2, 3), intArrayOf(5, 7), intArrayOf(11, 4),
            intArrayOf(8, 12), intArrayOf(13, 11), intArrayOf(3, 13),
            intArrayOf(10, 3), intArrayOf(1, 8), intArrayOf(14, 6), intArrayOf(6, 1)
        )
        for (d in dots) {
            c.set(d[0], d[1], dark)
            c.set(d[0] + if (d[0] % 2 == 0) 1 else -1, d[1], grassColLight)
        }
        return c.toBitmap()
    }

    fun tallGrass(): Bitmap {
        val c = Canvas2D(SIZE, SIZE)
        c.px.fill(grassBase[1])
        for (x in intArrayOf(3, 6, 9, 12)) {
            for (yy in 3..13) {
                c.set(x, yy, grassDark[1])
                c.set(x + 1, yy, if (yy % 3 == 0) 0xFF4FB54E.toInt() else grassDark[1])
            }
            c.set(x - 1, 12, grassColLight)
        }
        for (d in arrayOf(intArrayOf(1, 10), intArrayOf(13, 10), intArrayOf(7, 14))) {
            c.set(d[0], d[1], grassDark[1])
        }
        return c.toBitmap()
    }

    fun flower(variant: Int = 0): Bitmap {
        val petal = flowerColors[variant.rem(flowerColors.size)]
        val c = Canvas2D(SIZE, SIZE)
        c.px.fill(grassBase[0])
        for (d in arrayOf(intArrayOf(2, 3), intArrayOf(5, 8), intArrayOf(11, 3), intArrayOf(8, 13), intArrayOf(14, 9))) {
            c.set(d[0], d[1], grassDark[0])
            c.set(d[0] + 1, d[1], grassColLight)
        }
        val fy = 4
        val fx = 4
        c.set(fx, fy, petal)
        c.set(fx + 2, fy, petal)
        c.set(fx + 1, fy + 1, 0xFFF5C542.toInt())
        c.set(fx, fy + 2, petal)
        c.set(fx + 2, fy + 2, petal)
        return c.toBitmap()
    }

    fun tree(): Bitmap {
        val c = Canvas2D(SIZE, SIZE)
        val canopy = 0xFF2F8F3E.toInt()
        val canopyLight = 0xFF4FBD5C.toInt()
        val canopyDark = 0xFF1F6B2C.toInt()
        val trunk = 0xFF8A5A30.toInt()
        val trunkDark = 0xFF6B4423.toInt()
        for (y in 0..10) for (x in 1..14) c.set(x, y, canopy)
        for (y in 0..9) for (x in 2..13) if ((x + y) % 3 == 0) c.set(x, y, canopyLight)
        for (y in 2..8) for (x in 3..12) if ((x * 2 + y) % 4 == 0) c.set(x, y, canopyDark)
        for (y in 11..13) for (x in 6..9) c.set(x, y, trunk)
        for (y in 11..13) c.set(8, y, trunkDark)
        return c.toBitmap()
    }

    fun water(): Bitmap {
        val c = Canvas2D(SIZE, SIZE)
        c.px.fill(waterBase)
        for (y in 1..14) for (x in 1..14) {
            if ((x + y) % 4 == 1) c.set(x, y, waterDark) else if ((x + y) % 4 == 3) c.set(x, y, waterBase + 0x00040808)
        }
        c.set(4, 3, waterLight)
        c.set(4, 4, waterLight)
        c.set(11, 9, waterLight)
        c.set(11, 10, waterLight)
        c.set(12, 10, waterLight)
        return c.toBitmap()
    }

    fun path(): Bitmap {
        val c = Canvas2D(SIZE, SIZE)
        c.px.fill(0xFFD8B56B.toInt())
        for (d in arrayOf(intArrayOf(2, 3), intArrayOf(7, 7), intArrayOf(12, 4), intArrayOf(4, 12), intArrayOf(11, 12), intArrayOf(8, 2), intArrayOf(3, 7), intArrayOf(14, 8))) {
            val shade = 0xFFC29C52.toInt()
            c.set(d[0], d[1], shade)
            c.set(d[0], d[1] + 1, if (d[1] > 5) shade else 0xFFEAC98A.toInt())
        }
        return c.toBitmap()
    }

    fun rock(): Bitmap {
        val c = Canvas2D(SIZE, SIZE)
        val s0 = 0xFF8A929E.toInt()
        val hi = 0xFFB9C2CD.toInt()
        val sh = 0xFF5D656F.toInt()
        for (y in 3..11) for (x in 2..13) c.set(x, y, s0)
        for (y in 3..5) for (x in 3..9) if ((x + y) % 2 == 0) c.set(x, y, hi)
        c.set(2, 3, hi)
        c.set(13, 3, hi)
        for (y in 3..10) { c.set(2, y, hi); c.set(13, y, sh) }
        for (x in 2..13) { c.set(x, 11, sh); if (x % 3 == 0) c.set(x, 10, sh) }
        return c.toBitmap()
    }
}

object PlayerArt {
    private val CAP = 0xFFD94B4B.toInt()
    private val CLOTH = 0xFF3D4A63.toInt()
    private val SKIN = 0xFFB57B4F.toInt()
    private val SKIN_D = 0xFF8E5C35.toInt()

    private val WALK_A = listOf(
        "....oooooo....",
        "..ocCCCCCCoo..",
        ".oCCCCCCCCCCo.",
        ".oCCCCCCCCCCo.",
        ".oSCCCCCCCCoo.",
        ".oSSSSSSSSSSo.",
        ".oSWWSSSSWWSo.",
        ".oSSwSSSSwSSo.",
        ".oSSSMMMMSSSo.",
        ".oSSSSSSSSSSo.",
        ".oKKSSKSSSKKo.",
        ".oKKKKKKKKKKo.",
        "..oKKKKKKKKo..",
        "...oooooooo...",
    )

    private val WALK_B = listOf(
        "....oooooo....",
        "..ocCCCCCCoo..",
        ".oCCCCCCCCCCo.",
        ".oCCCCCCCCCCo.",
        ".oSCCCCCCCCoo.",
        ".oSSSSSSSSSSo.",
        ".oSWWSSSSWWSo.",
        ".oSSwSSSSwSSo.",
        ".oSSSMMMMSSSo.",
        ".oSSSSSSSSSSo.",
        ".oKKSSKSSSKKo.",
        ".oKKKKKKKKKKo.",
        "..oKKKKKKKKoo.",
        "...oooooooo...",
    )

    fun render(skin: SkinDef): List<Bitmap> {
        val c = skin.cap
        val k = skin.cloth
        val s = skin.hair
        val pal = mapOf(
            'o' to 0xFF20242E.toInt(),
            'c' to c,
            'C' to c,
            'S' to s,
            'W' to 0xFFF4F6FF.toInt(),
            'w' to 0xFF1B2233.toInt(),
            'M' to 0xFFE2605A.toInt(),
            'K' to k,
            'k' to k
        )
        return listOf(parse(WALK_A, pal).toBitmap(), parse(WALK_B, pal).toBitmap())
    }

    val FALLBACK = render(Skins.LIST[0])
}