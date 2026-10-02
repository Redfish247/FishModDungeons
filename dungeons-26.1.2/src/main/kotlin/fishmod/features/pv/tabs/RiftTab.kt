package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.pv.*
import kotlin.math.max

object RiftTab : PvTab {
    override val id = "rift"
    override val title = "Rift"

    private const val GAP = 10
    private const val ROW = 12

    // id fragment -> display name
    private val PORHTALS = listOf(
        "wyld_woods" to "Wyld Woods", "black_lagoon" to "Black Lagoon", "west_village" to "West Village",
        "village_plaza" to "Village Plaza", "mirrorverse" to "Mirrorverse", "living_cave" to "Living Cave",
        "colosseum" to "Colosseum", "barrier_street" to "Barrier Street", "mountaintop" to "Mountaintop",
    )
    private val TIMECHARMS = listOf(
        "wyldly_supreme" to "Supreme Timecharm", "mirrored" to "mrahcemiT esrevrorriM", "chicken_n_egg" to "Chicken N Egg Timecharm",
        "citizen" to "SkyBlock Citizen Timecharm", "lazy_living" to "Living Timecharm", "slime" to "Globulate Timecharm",
        "vampiric" to "Vampiric Timecharm", "mountain" to "Celestial Timecharm",
    )

    override fun height(c: PvCtx, area: PvRect): Int {
        val h = heights(c)
        return if (area.w < 520) max(h[0], h[1]) + GAP + max(h[2], h[3]) else h.max()
    }

    private fun heights(c: PvCtx) = intArrayOf(130, 21 + statRows(c).size * ROW + 6, 21 + PORHTALS.size * ROW + 6, 21 + TIMECHARMS.size * 20 + 4)

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val r = c.member.rift
        if (r == null) { c.text("No Rift data on this profile", area.x, area.y + 4, c.theme.mut); return }
        val h = heights(c)
        val narrow = area.w < 520
        val cols = if (narrow) 2 else 4
        val cw = (area.w - GAP * (cols - 1)) / cols
        fun pos(i: Int): Pair<Int, Int> = if (narrow) (area.x + (i % 2) * (cw + GAP)) to (area.y + if (i >= 2) max(h[0], h[1]) + GAP else 0)
            else (area.x + i * (cw + GAP)) to area.y
        pos(0).let { (x, y) -> gearCard(c, r, x, y, cw, h[0]) }
        pos(1).let { (x, y) -> statsCard(c, x, y, cw, h[1]) }
        pos(2).let { (x, y) -> porhtalCard(c, r.raw, x, y, cw, h[2]) }
        pos(3).let { (x, y) -> charmCard(c, r.raw, x, y, cw, h[3]) }
    }

    private fun gearCard(c: PvCtx, r: PvRift, x: Int, y: Int, w: Int, h: Int) {
        val t = c.theme
        var cy = c.card(x, y, w, h, "Armor & Equipment")
        for ((label, items) in listOf("Armor" to r.armor.take(4), "Equipment" to r.equipment.items.take(4))) {
            c.text(label, x + 9, cy, t.mut, PvCtx.S_XS); cy += 9
            val bonus = statSum(items)
            val line = if (bonus.isEmpty()) "§8No bonus" else bonus.entries.joinToString(" §8// ") { "§a${num(it.value)} §7${short(it.key)}" }
            val bw = c.legacy(clip(c, line, w - 18), x + 9, cy, PvCtx.S_XS, t.fg)
            if (bonus.isNotEmpty()) c.tip(x + 9, cy - 1, bw, 9, listOf("§f$label bonus") + bonus.map { "§7${it.key}: §a+${num(it.value)}" })
            cy += 10
            for (i in 0 until 4) c.item(items.getOrNull(i), x + 9 + i * 20, cy)
            cy += 24
        }
    }

    private fun statRows(c: PvCtx): List<Triple<String, String, List<String>>> {
        val m = c.member; val raw = m.raw; val r = m.rift ?: return emptyList()
        val out = ArrayList<Triple<String, String, List<String>>>()
        raw.long("player_stats", "rift", "visits")?.let { out += Triple("Visits", full(it.toDouble()), emptyList()) }
        out += Triple("Motes", "§d" + fmt(r.motes?.toDouble() ?: 0.0), listOf("§fMotes", "§7Purse: §d${full(r.motes?.toDouble() ?: 0.0)}",
            "§7Lifetime: §d${full(r.lifetimeMotes?.toDouble() ?: 0.0)}"))
        val souls = r.enigmaSouls ?: 0
        out += Triple("Enigma Souls", (if (souls >= 52) "§6" else "§f") + "$souls / 52", emptyList())
        val burgers = (raw.int("rift", "castle", "grubber_stacks") ?: 0).coerceAtMost(5)
        out += Triple("McGrubber's Burgers", (if (burgers >= 5) "§6" else "§f") + "$burgers / 5", emptyList())
        val eyes = r.raw.arr("wither_cage", "killed_eyes")?.size() ?: 0
        out += Triple("Wither Eyes", full(eyes.toDouble()), emptyList())
        r.montezumaCats?.let { out += Triple("Montezuma Cats", "$it", emptyList()) }
        m.slayers.firstOrNull { it.def.key == "vampire" }?.let { s ->
            out += Triple("Vampire Slayer", "§c${s.level}", listOf("§fRiftstalker Bloodfiend ${s.level}", "§7XP: §f${full(s.xp.toDouble())}", "§7Kills: §f${s.totalKills}"))
        }
        return out
    }

    private fun statsCard(c: PvCtx, x: Int, y: Int, w: Int, h: Int) {
        var cy = c.card(x, y, w, h, "Rift Stats")
        for ((k, v, tip) in statRows(c)) {
            c.text(k, x + 9, cy, c.theme.mut, PvCtx.S_SM)
            val vw = c.textW(v, PvCtx.S_SM)
            c.legacy(v, x + w - 9 - vw, cy, PvCtx.S_SM)
            c.tip(x + 4, cy - 1, w - 8, ROW, tip)
            cy += ROW
        }
    }

    private fun porhtalCard(c: PvCtx, rift: JsonObject?, x: Int, y: Int, w: Int, h: Int) {
        // Wyld Woods is free; the rest are rift.lifetime_purchased_boundaries
        val got = (rift?.getAsJsonArray("lifetime_purchased_boundaries")
            ?.mapNotNull { runCatching { it.asString.lowercase() }.getOrNull() }.orEmpty()) + "wyld_woods"
        var cy = c.card(x, y, w, h, "Porhtals §8${PORHTALS.count { p -> got.any { it.contains(p.first) } }}/${PORHTALS.size}")
        for ((key, name) in PORHTALS) {
            val on = got.any { it.contains(key) }
            c.rect(x + 9, cy + 2, 4, 4, if (on) c.theme.acc else c.theme.track, 2f)
            c.text(name, x + 17, cy, if (on) c.theme.fg else c.theme.mut and 0x80FFFFFF.toInt(), PvCtx.S_SM)
            cy += ROW
        }
    }

    private fun charmCard(c: PvCtx, rift: JsonObject?, x: Int, y: Int, w: Int, h: Int) {
        val got = HashMap<String, Long>()
        rift.arr("gallery", "secured_trophies")?.forEach { e ->
            runCatching { val o = e.asJsonObject; got[o.get("type").asString] = o.get("timestamp")?.asLong ?: 0L }
        }
        var cy = c.card(x, y, w, h, "Timecharms §8${got.size}/${TIMECHARMS.size}")
        for ((key, name) in TIMECHARMS) {
            val ts = got[key]
            val col = if (ts != null) c.theme.fg else c.theme.mut
            c.text(name, x + 9, cy, col, PvCtx.S_SM)
            c.text(if (ts == null) "Not obtained" else "Obtained ${ago(ts)}", x + 9, cy + 8, if (ts != null) c.theme.acc else c.theme.line, PvCtx.S_XS)
            cy += 20
        }
    }

    private val STAT = Regex("^([A-Za-z ]+): \\+([\\d,.]+)")
    private fun statSum(items: List<PvItem?>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (it in items) for (l in it?.lore ?: continue) {
            val m = STAT.find(l.replace(Regex("§."), "").trim()) ?: continue
            val v = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            out.merge(m.groupValues[1].trim(), v, Double::plus)
        }
        return out
    }

    private fun short(k: String) = k.split(' ').joinToString("") { it.take(if (k.contains(' ')) 1 else 3) }
    private fun num(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
    private fun clip(c: PvCtx, s: String, w: Int): String {
        if (c.textW(s, PvCtx.S_XS) <= w) return s
        var out = s
        while (out.length > 4 && c.textW("$out…", PvCtx.S_XS) > w) out = out.dropLast(1)
        return "$out…"
    }
    private fun ago(ms: Long): String {
        val d = (System.currentTimeMillis() - ms) / 86_400_000L
        return when { d >= 365 -> "${d / 365} year${if (d >= 730) "s" else ""} ago"; d >= 30 -> "${d / 30} months ago"; else -> "$d days ago" }
    }
}
