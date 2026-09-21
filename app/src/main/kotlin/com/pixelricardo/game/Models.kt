package com.pixelricardo.game

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Species(
    val id: Int,
    val name: String,
    val baseHue: Float,
    val element: String,
    val desc: String
)

data class CreatureState(var care: Int = 0, var stage: Int = 0)

object Catalog {
    val SPECIES = listOf(
        Species(0, "Mei", 0.03f, "Fogo", "Pequena e ardente. Adora pizza picante."),
        Species(1, "Gota", 0.55f, "Água", "Solta bolhinhas quando está feliz."),
        Species(2, "Folhinha", 0.33f, "Planta", "Faz gracinha com folha no topo."),
        Species(3, "Zumi", 0.10f, "Elétrico", "Estala energia de tão empolgada."),
        Species(4, "Pupita", 0.47f, "Fada", "Espalha purpurina por onde passa."),
        Species(5, "Bolinha", 0.88f, "Terra", "Rola pela grama e se suja de alegria."),
        Species(6, "Puff", 0.65f, "Ar", "Flutua com o vento da tarde."),
        Species(7, "Nicka", 0.15f, "Fogo", "Faz fogueirinha para as amigas."),
        Species(8, "Flo", 0.72f, "Fada", "Canta baixinho antes de dormir."),
        Species(9, "Tingo", 0.27f, "Planta", "Cultiva flores no gramado."),
        Species(10, "Lume", 0.05f, "Elétrico", "Acende a luz do quarto sozinha."),
        Species(11, "Coco", 0.80f, "Terra", "Dorme fazendo xon-xon."),
        Species(12, "Mina", 0.58f, "Água", "Enche a piscina de bolhas."),
        Species(13, "Sena", 0.43f, "Planta", "Espalha sementinhas no céu."),
        Species(14, "Luna", 0.68f, "Ar", "Voa perto da lua todas as noites."),
        Species(15, "Roco", 0.02f, "Terra", "Faz barulhinho de pedrinha."),
        Species(16, "Fia", 0.50f, "Fada", "Deixa trilhas de brilho no chão."),
        Species(17, "Sungo", 0.37f, "Planta", "Banha de sol e faz fotossíntese fofa."),
        Species(18, "Duda", 0.09f, "Elétrico", "Dá choquinho de amor (fraquinho)."),
        Species(19, "Pipa", 0.60f, "Água", "Chove quando fica triste."),
        Species(20, "Kiko", 0.90f, "Fogo", "Assa marshmallows para as amigas."),
        Species(21, "Zamp", 0.20f, "Terra", "Cria túneis secretos."),
        Species(22, "Riso", 0.70f, "Ar", "Ri com o vento (hehe ao vento)."),
        Species(23, "Fantoche", 0.12f, "Fada", "Esconde coisinhas brilhantes."),
    )
    val byId = SPECIES.associateBy { it.id }

    val NUM_SPECIES = SPECIES.size

    fun randomId(): Int = SPECIES.random().id
}

data class AchDef(
    val id: Int,
    val name: String,
    val desc: String,
    val target: Int,
    val reward: Int
)

object Achievements {
    val DEFS = listOf(
        AchDef(0, "Primeira Amiga", "Capture sua 1ª criatura", 1, 20),
        AchDef(1, "Colecionadora", "Capture 8 espécies diferentes", 8, 40),
        AchDef(2, "Melhor Amiga", "Capture 16 espécies diferentes", 16, 80),
        AchDef(3, "Enciclopédia Viva", "Capture todas as 24 espécies", 24, 200),
        AchDef(4, "Dengosa", "Faça 10 cuidados com suas criaturas", 10, 20),
        AchDef(5, "Cuidadora de Coração", "Faça 60 cuidados", 60, 60),
        AchDef(6, "Hospedeira Perfeita", "Faça 200 cuidados", 200, 200),
        AchDef(7, "Crescendo Forte", "Evolua 1 criatura", 1, 40),
        AchDef(8, "Gigante Gentil", "Evolua 6 criaturas", 6, 100),
        AchDef(9, "Lenda Viva", "Evolua 15 criaturas", 15, 300),
        AchDef(10, "Curiosa", "Explore 40 áreas diferentes do mundo", 40, 20),
        AchDef(11, "Aventureira", "Explore 120 áreas diferentes", 120, 60),
        AchDef(12, "Mestre do Mapa", "Explore 300 áreas diferentes", 300, 150),
        AchDef(13, "Fiel", "Jogue 3 dias seguidos", 3, 30),
        AchDef(14, "Amiga de Todo Dia", "Jogue 7 dias seguidos", 7, 120),
        AchDef(15, "Lenda da Semana", "Jogue 14 dias seguidos", 14, 500),
        AchDef(16, "Coletando Pedrinhas", "Ganhe 50 pedrinhas", 50, 30),
        AchDef(17, "Minha Fazenda", "Ganhe 500 pedrinhas", 500, 250),
        AchDef(18, "Peixinho Dourado", "Capture uma criatura rara dourada", 1, 75),
        AchDef(19, "Estilista", "Compre 3 visuais", 3, 60),
    )
    val byId = DEFS.associateBy { it.id }

    fun progressFor(s: SaveData, a: AchDef): Int = when (a.id) {
        0, 1, 2, 3 -> s.caught.size.coerceAtMost(a.target)
        4, 5, 6 -> s.careTotal.coerceAtMost(a.target)
        7, 8, 9 -> s.evolveTotal.coerceAtMost(a.target)
        10, 11, 12 -> s.visited.size.coerceAtMost(a.target)
        13, 14, 15 -> s.streak.coerceAtMost(a.target)
        16, 17 -> s.coinsEarned.coerceAtMost(a.target)
        18 -> if (s.caughtRare) 1 else 0
        19 -> (s.ownedSkins.size - 1).coerceIn(0, a.target)
        else -> 0
    }
}

class SaveData {
    var coins = 0
    var coinsEarned = 0
    var streak = 0
    var lastDay = ""
    var playCount = 0
    var difficulty = 1
    var skin = 0
    val ownedSkins = mutableSetOf(0)
    val caught = mutableMapOf<Int, CreatureState>()
    val unlockedAch = mutableSetOf<Int>()
    var careTotal = 0
    var catchTotal = 0
    var evolveTotal = 0
    val visited = mutableSetOf<String>()
    var caughtRare = false

    fun toJson(): String {
        val o = JSONObject()
        o.put("coins", coins)
        o.put("coinsEarned", coinsEarned)
        o.put("streak", streak)
        o.put("lastDay", lastDay)
        o.put("playCount", playCount)
        o.put("difficulty", difficulty)
        o.put("skin", skin)
        o.put("ownedSkins", JSONArray(ownedSkins.toList()))
        val ca = JSONObject()
        caught.forEach { (k, v) ->
            val cs = JSONObject()
            cs.put("care", v.care)
            cs.put("stage", v.stage)
            ca.put(k.toString(), cs)
        }
        o.put("caught", ca)
        o.put("unlockedAch", JSONArray(unlockedAch.toList()))
        o.put("careTotal", careTotal)
        o.put("catchTotal", catchTotal)
        o.put("evolveTotal", evolveTotal)
        o.put("visited", JSONArray(visited.toList()))
        o.put("caughtRare", caughtRare)
        return o.toString()
    }

    companion object {
        fun fromJson(s: String): SaveData {
            val d = SaveData()
            val o = JSONObject(s)
            d.coins = o.optInt("coins", 0)
            d.coinsEarned = o.optInt("coinsEarned", 0)
            d.streak = o.optInt("streak", 0)
            d.lastDay = o.optString("lastDay", "")
            d.playCount = o.optInt("playCount", 0)
            d.difficulty = o.optInt("difficulty", 1)
            d.skin = o.optInt("skin", 0)
            val os = o.optJSONArray("ownedSkins")
            if (os != null) for (i in 0 until os.length()) d.ownedSkins.add(os.getInt(i))
            val ca = o.optJSONObject("caught")
            if (ca != null) {
                val it = ca.keys()
                while (it.hasNext()) {
                    val k = it.next().toInt()
                    val cs = ca.getJSONObject(k.toString())
                    d.caught[k] = CreatureState(cs.optInt("care", 0), cs.optInt("stage", 0))
                }
            }
            val ua = o.optJSONArray("unlockedAch")
            if (ua != null) for (i in 0 until ua.length()) d.unlockedAch.add(ua.getInt(i))
            d.careTotal = o.optInt("careTotal", 0)
            d.catchTotal = o.optInt("catchTotal", 0)
            d.evolveTotal = o.optInt("evolveTotal", 0)
            val v = o.optJSONArray("visited")
            if (v != null) for (i in 0 until v.length()) d.visited.add(v.getString(i))
            d.caughtRare = o.optBoolean("caughtRare", false)
            return d
        }
    }
}

data class SkinDef(
    val id: Int,
    val name: String,
    val price: Int,
    val cap: Int,
    val cloth: Int,
    val hair: Int
)

object Skins {
    val LIST = listOf(
        SkinDef(0, "Aventureira", 0, 0xFFD94B4B.toInt(), 0xFF3D4A63.toInt(), 0xFFB57B4F.toInt()),
        SkinDef(1, "Capitã do Mar", 20, 0xFF2E4A8F.toInt(), 0xFFFFFFFF.toInt(), 0xFFB57B4F.toInt()),
        SkinDef(2, "Estrelinha", 25, 0xFF7A4FA8.toInt(), 0xFFB09A3C.toInt(), 0xFFB57B4F.toInt()),
        SkinDef(3, "Aventureira Verde", 30, 0xFF3E8F4F.toInt(), 0xFF6B4A2E.toInt(), 0xFF8A5A30.toInt()),
        SkinDef(4, "Rainha das Sombras", 40, 0xFF2B2B35.toInt(), 0xFF8E44AD.toInt(), 0xFFE7E7EE.toInt()),
        SkinDef(5, "Princesa dos Pixels", 60, 0xFFE86AA6.toInt(), 0xFFFFE97A.toInt(), 0xFFE7AA3C.toInt()),
    )
    val byId = LIST.associateBy { it.id }
}

object SaveIO {
    fun file(context: android.content.Context): File =
        File(context.filesDir, "pixelricardo_save.json")

    fun load(context: android.content.Context): SaveData {
        val f = file(context)
        return if (f.exists()) {
            try {
                SaveData.fromJson(f.readText())
            } catch (e: Exception) {
                SaveData()
            }
        } else SaveData()
    }

    fun persist(context: android.content.Context, d: SaveData) {
        try {
            file(context).writeText(d.toJson())
        } catch (e: Exception) {
        }
    }
}

fun todayStr(): String = LocalDate.now().toString()