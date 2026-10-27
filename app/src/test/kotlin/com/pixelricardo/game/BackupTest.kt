package com.pixelricardo.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesProgress() {
        val save = SaveData().apply {
            coins = 123; skin = 2; ownedSkins.add(2)
            caught[4] = CreatureState(81, 2)
            unlockedAch.add(7); visited.add("3,4")
            lastDay = "2026-10-27"; questCombo = 20
            questElement["Fogo"] = 2; soundOn = false
        }
        assertEquals(save.toJson(), Backup.decode(Backup.encode(save)).toJson())
    }

    @Test fun rejectsInvalidData() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("format", "AnotherGame") },
            { it.put("version", 2) },
            { it.getJSONObject("save").remove("coins") },
            { it.getJSONObject("save").put("coins", -1) },
            { it.getJSONObject("save").put("coins", "12") },
            { it.getJSONObject("save").put("difficulty", 9) },
            { it.getJSONObject("save").put("questDate", "not-a-date") },
            { it.getJSONObject("save").getJSONObject("caught").put("99", JSONObject().put("care", 0).put("stage", 0)) },
            { it.getJSONObject("save").getJSONObject("caught").put("0", JSONObject().put("care", 0).put("stage", 3)) },
            { it.getJSONObject("save").getJSONArray("visited").put("100,100") },
            { it.getJSONObject("save").getJSONArray("ownedSkins").put(99) },
            { it.getJSONObject("save").getJSONObject("questElement").put("Fogo", -3) }
        )
        for (mutate in mutations) {
            val root = JSONObject(Backup.encode(SaveData()))
            mutate(root)
            assertThrows(Exception::class.java) { Backup.decode(root.toString()) }
        }
        assertThrows(Exception::class.java) { Backup.decode("{}") }
        assertThrows(Exception::class.java) { Backup.decode(" ".repeat(Backup.MAX_BYTES + 1)) }
    }
}
