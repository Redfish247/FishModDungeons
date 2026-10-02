package fishmod.features.pv

import fishmod.utils.HypixelApi

// Level tables + xp->level math for the profile viewer.
object PvTables {

    class Level(val level: Int, val progress: Double, val xpInto: Long, val xpNeeded: Long, val maxed: Boolean, val cap: Int, val totalXp: Long) {
        val fractional get() = level + if (maxed) 0.0 else progress
    }

    private val SKILL_PER = longArrayOf(
        50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
        5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
        300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
        1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
        2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
        4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000,
    )
    private val RUNECRAFTING_PER = longArrayOf(
        50, 100, 125, 160, 200, 250, 315, 400, 500, 625, 785, 1_000, 1_250, 1_600, 2_000,
        2_465, 3_125, 4_000, 5_000, 6_200, 7_800, 9_800, 12_200, 15_300, 19_050,
    )
    private val SOCIAL_PER = longArrayOf(
        50, 100, 150, 250, 500, 750, 1_000, 1_250, 1_500, 2_000, 2_500, 3_000, 3_750, 4_500, 6_000,
        8_000, 10_000, 12_500, 15_000, 20_000, 25_000, 30_000, 35_000, 40_000, 50_000,
    )
    private val DUNGEON_PER: LongArray = LongArray(50) { HypixelApi.CATA_XP_TABLE[it + 1] - HypixelApi.CATA_XP_TABLE[it] }

    val SKILLS = listOf(
        "farming", "mining", "combat", "foraging", "fishing", "enchanting",
        "alchemy", "taming", "hunting", "carpentry", "runecrafting", "social",
    )
    val COSMETIC_SKILLS = setOf("runecrafting", "social")

    fun skillCap(skill: String, farmingCapBonus: Int = 0, tamingCapBonus: Int = 10): Int = when (skill) {
        "farming" -> 50 + farmingCapBonus.coerceIn(0, 10)
        "taming" -> 50 + tamingCapBonus.coerceIn(0, 10)
        "mining", "combat", "enchanting" -> 60
        "foraging" -> 54
        "fishing", "alchemy", "carpentry", "hunting" -> 50
        "runecrafting", "social" -> 25
        else -> 50
    }

    fun skill(skill: String, xp: Long, cap: Int): Level = when (skill) {
        "runecrafting" -> level(RUNECRAFTING_PER, xp, cap)
        "social" -> level(SOCIAL_PER, xp, cap)
        else -> level(SKILL_PER, xp, cap)
    }

    fun dungeon(xp: Long): Level = level(DUNGEON_PER, xp, 50)

    private fun level(per: LongArray, xp: Long, cap: Int): Level {
        val max = minOf(cap, per.size)
        var lvl = 0
        var rem = xp.coerceAtLeast(0)
        while (lvl < max && rem >= per[lvl]) { rem -= per[lvl]; lvl++ }
        if (lvl >= max) return Level(max, 1.0, rem, 0, true, cap, xp)
        val need = per[lvl]
        return Level(lvl, rem.toDouble() / need, rem, need, false, cap, xp)
    }

    // Slayers: cumulative xp per level and tiers that exist.
    class Slayer(val key: String, val name: String, val short: String, val thresholds: LongArray, val tiers: Int)

    private val ZOMBIE = longArrayOf(5, 15, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000)
    private val SPIDER = longArrayOf(5, 25, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000)
    private val WOLF = longArrayOf(10, 30, 250, 1_500, 5_000, 20_000, 100_000, 400_000, 1_000_000)
    private val VAMPIRE = longArrayOf(20, 75, 240, 840, 2_400)

    val SLAYERS = listOf(
        Slayer("zombie", "Revenant Horror", "Rev", ZOMBIE, 5),
        Slayer("spider", "Tarantula Broodfather", "Tara", SPIDER, 5),
        Slayer("wolf", "Sven Packmaster", "Sven", WOLF, 4),
        Slayer("enderman", "Voidgloom Seraph", "Eman", WOLF, 4),
        Slayer("blaze", "Inferno Demonlord", "Blaze", WOLF, 4),
        Slayer("vampire", "Riftstalker Bloodfiend", "Vamp", VAMPIRE, 5),
    )

    fun slayerLevel(s: Slayer, xp: Long): Int = s.thresholds.count { xp >= it }

    val CLASSES = listOf("healer", "mage", "berserk", "archer", "tank")

    // Pets: per-level xp list; rarity offset into it.
    private val PET_XP = intArrayOf(
        100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600,
        660, 730, 800, 880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920, 3160, 3420,
        3700, 4000, 4350, 4750, 5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300, 14700, 16200, 17800, 19500, 21300, 23200,
        25200, 27400, 29800, 32400, 35200, 38200, 41400, 44800, 48400, 52200, 56200, 60400, 64800, 69400, 74200, 79200, 84700, 90700, 97200, 104200,
        111700, 119700, 128200, 137200, 146700, 156700, 167700, 179700, 192700, 206700, 221700, 237700, 254700, 272700, 291700, 311700, 333700, 357700, 383700, 411700,
        441700, 476700, 516700, 561700, 611700, 666700, 726700, 791700, 861700, 936700, 1016700, 1101700, 1191700, 1286700, 1386700, 1496700, 1616700, 1746700, 1886700,
    )
    private val PET_OFFSET = mapOf("COMMON" to 0, "UNCOMMON" to 6, "RARE" to 11, "EPIC" to 16, "LEGENDARY" to 20, "MYTHIC" to 20)

    fun petMaxLevel(type: String): Int = if (type == "GOLDEN_DRAGON" || type == "JADE_DRAGON" || type == "ROSE_DRAGON") 200 else 100

    fun petLevel(xp: Double, tier: String, type: String): Level {
        val off = PET_OFFSET[tier] ?: 20
        val max = petMaxLevel(type)
        var lvl = 1
        var rem = xp.coerceAtLeast(0.0)
        while (lvl < max) {
            val idx = off + lvl - 1
            val need = if (lvl >= 100) 1_886_700 else PET_XP.getOrElse(idx) { 1_886_700 }
            if (rem < need) return Level(lvl, rem / need, rem.toLong(), need.toLong(), false, max, xp.toLong())
            rem -= need; lvl++
        }
        return Level(max, 1.0, rem.toLong(), 0, true, max, xp.toLong())
    }

    // SkyBlock level: 100 xp per level.
    fun sbLevel(xp: Double): Level {
        val lvl = (xp / 100).toInt()
        val into = (xp - lvl * 100).toLong()
        return Level(lvl, into / 100.0, into, 100, false, 999, xp.toLong())
    }

    val RARITY_COLOR = mapOf(
        "COMMON" to 0xFFFFFFFF.toInt(), "UNCOMMON" to 0xFF55FF55.toInt(), "RARE" to 0xFF5555FF.toInt(),
        "EPIC" to 0xFFAA00AA.toInt(), "LEGENDARY" to 0xFFFFAA00.toInt(), "MYTHIC" to 0xFFFF55FF.toInt(),
        "DIVINE" to 0xFF55FFFF.toInt(), "SPECIAL" to 0xFFFF5555.toInt(), "VERY_SPECIAL" to 0xFFFF5555.toInt(),
    )
    val RARITY_CODE = mapOf(
        "COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6",
        "MYTHIC" to "§d", "DIVINE" to "§b", "SPECIAL" to "§c", "VERY_SPECIAL" to "§c",
    )
    val RARITY_MP = mapOf(
        "COMMON" to 3, "UNCOMMON" to 5, "RARE" to 8, "EPIC" to 12, "LEGENDARY" to 16,
        "MYTHIC" to 22, "SPECIAL" to 3, "VERY_SPECIAL" to 5,
    )

    fun rarityFromLore(lore: List<String>): String? {
        for (i in lore.indices.reversed()) {
            val l = lore[i].replace(Regex("§."), "").trim()
            if (l.isEmpty()) continue
            val s = l.removePrefix("a ").removePrefix("SHINY ")
            for (r in listOf("VERY SPECIAL", "SPECIAL", "MYTHIC", "DIVINE", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"))
                if (s.startsWith(r)) return r.replace(' ', '_')
            return null
        }
        return null
    }

    val FLOORS = (0..7).toList()
}
