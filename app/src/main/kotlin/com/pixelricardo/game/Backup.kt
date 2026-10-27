package com.pixelricardo.game

import org.json.JSONObject

/** Portable, versioned backup; deliberately separate from the permissive local save loader. */
object Backup {
    const val MAX_BYTES = 1024 * 1024

    fun encode(save: SaveData): String = JSONObject()
        .put("format", "PixelRicardo").put("version", 1)
        .put("save", JSONObject(save.toJson())).toString(2)

    fun decode(text: String): SaveData {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getString("format") == "PixelRicardo")
        require(root.get("version") == 1)
        val data = root.getJSONObject("save")
        val template = JSONObject(SaveData().toJson())
        // Require every field exported by this format and reject coercion of invalid types.
        for (key in template.keys()) {
            val value = data.get(key)
            when (template.get(key)) {
                is Int -> require(value is Int && value >= 0)
                is Boolean -> require(value is Boolean)
                is String -> require(value is String)
                is org.json.JSONArray -> require(value is org.json.JSONArray)
                is JSONObject -> require(value is JSONObject)
            }
        }
        for (key in listOf("lastDay", "lastGiftDay", "lastCatchDay", "questDate")) {
            val date = data.getString(key)
            if (date.isNotEmpty()) java.time.LocalDate.parse(date)
        }
        for (key in listOf("ownedSkins", "unlockedAch")) {
            val array = data.getJSONArray(key)
            val allowed = if (key == "ownedSkins") Skins.byId.keys else Achievements.byId.keys
            for (i in 0 until array.length()) require(array.get(i) is Int && array.getInt(i) in allowed)
        }
        val caught = data.getJSONObject("caught")
        for (key in caught.keys()) {
            require(key.toIntOrNull() in Catalog.SPECIES.map { it.id })
            val creature = caught.getJSONObject(key)
            require(creature.get("care") is Int && creature.getInt("care") >= 0)
            require(creature.get("stage") is Int && creature.getInt("stage") in 0..2)
        }
        val elements = Catalog.SPECIES.map { it.element }.toSet()
        val quest = data.getJSONObject("questElement")
        for (key in quest.keys()) {
            require(key in elements && quest.get(key) is Int && quest.getInt(key) >= 0)
        }
        val visited = data.getJSONArray("visited")
        for (i in 0 until visited.length()) {
            val cell = visited.get(i)
            require(cell is String && cell.matches(Regex("[0-9]+,[0-9]+")))
            val (x, y) = cell.split(',').map { it.toInt() }
            require(x in 0..25 && y in 0..17)
        }
        val save = SaveData.fromJson(data.toString())
        require(save.difficulty in 0..2 && save.skin in save.ownedSkins && save.skin in Skins.byId)
        return save
    }
}
