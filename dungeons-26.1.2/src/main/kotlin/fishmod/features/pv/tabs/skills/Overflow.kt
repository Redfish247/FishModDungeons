package fishmod.features.pv.tabs.skills

import fishmod.features.pv.PvTables
import fishmod.features.pv.full

// SkyCrypt-style overflow: past the cap, keep walking the table to 60, then 7M XP per level.
internal object Overflow {
    private val PER = longArrayOf(
        50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
        5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
        300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
        1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
        2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
        4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000,
    )
    private val cache = HashMap<PvTables.Level, PvTables.Level>()

    fun level(key: String, xp: Long?, base: PvTables.Level): PvTables.Level {
        if (xp == null || !base.maxed || key == "runecrafting" || key == "social") return base
        cache[base]?.let { return it }
        var rem = xp; var lv = 0
        while (lv < PER.size && rem >= PER[lv]) { rem -= PER[lv]; lv++ }
        var need = if (lv < PER.size) PER[lv] else PER.last()
        if (lv >= PER.size) { lv += (rem / need).toInt(); rem %= need }
        val out = if (lv <= base.level) base else PvTables.Level(lv, rem.toDouble() / need, rem, need, false, base.cap, xp)
        if (cache.size > 64) cache.clear()
        cache[base] = out
        return out
    }

    fun tip(l: PvTables.Level): List<String> = listOf("§6Overflow level ${l.level} §7(cap ${l.cap})", "§7Next: §f${full(l.xpInto.toDouble())}§7/§f${full(l.xpNeeded.toDouble())}")
}
