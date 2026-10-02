package fishmod.features.pv.tabs.skills

import fishmod.features.pv.PvTables
import fishmod.features.pv.full

// Overflow past the cap (SkyHanni curve via PvTables.levelWithOverflow).
internal object Overflow {
    private val cache = HashMap<PvTables.Level, PvTables.Level>()

    fun level(key: String, xp: Long?, base: PvTables.Level): PvTables.Level {
        if (xp == null) return base
        cache[base]?.let { return it }
        val o = PvTables.levelWithOverflow(key, xp, base.cap)
        val out = if (o.overflow) o else base
        if (cache.size > 64) cache.clear()
        cache[base] = out
        return out
    }

    fun tip(l: PvTables.Level): List<String> = listOf("§6Overflow level ${l.level} §7(cap ${l.cap})", "§7Next: §f${full(l.xpInto.toDouble())}§7/§f${full(l.xpNeeded.toDouble())}")
}
