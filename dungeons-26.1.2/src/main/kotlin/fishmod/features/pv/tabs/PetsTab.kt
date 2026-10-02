package fishmod.features.pv.tabs

import fishmod.features.pv.*

object PetsTab : PvTab {
    override val id = "pets"
    override val title = "Pets"

    private const val T = 20
    private const val GX = 6
    private const val CELL_H = T + 11
    private const val STATS_H = 4 * 12 + 16
    private const val ACTIVE_H = 21 + 28
    private val MF_STEPS = intArrayOf(10, 25, 50, 75, 100, 130, 175, 225, 275, 325)

    private fun cols(w: Int) = ((w - 18 + GX) / (T + GX)).coerceAtLeast(1)
    private fun others(c: PvCtx) = c.member.pets.filter { !it.active }
        .sortedWith(compareByDescending<PvPet> { PvData.RARITY_ORDER.indexOf(it.tier) }.thenByDescending { it.level.level }.thenByDescending { it.xp })
    private fun gridH(n: Int, w: Int) = if (n == 0) 12 else ((n + cols(w) - 1) / cols(w)) * CELL_H

    override fun height(c: PvCtx, area: PvRect) = STATS_H + 8 + ACTIVE_H + 8 + 21 + gridH(others(c).size, area.w) + 8

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme; val pets = c.member.pets
        var y = area.y

        // Stats
        c.card(area.x, y, area.w, STATS_H)
        var sy = y + 8
        val unique = pets.map { it.type }.distinct().size
        stat(c, area.x + 9, sy, "Unique Pets:", "$unique", listOf("§fUnique pet types: §a$unique", "§7Total pets: §f${pets.size}")); sy += 12
        val best = pets.groupBy { it.type }.mapValues { (_, l) -> l.maxOf { p -> PvData.RARITY_ORDER.indexOf(p.tier).coerceIn(0, 5) + 1 + if (p.level.maxed) 1 else 0 } }
        val score = best.values.sum()
        val mf = MF_STEPS.count { score >= it }
        val next = MF_STEPS.firstOrNull { score < it }
        stat(c, area.x + 9, sy, "Pet Score:", "$score (+$mf MF)", listOfNotNull("§fPet Score", "§7Highest rarity per pet type (+1 if max level)",
            "§7Magic Find: §b+$mf", next?.let { "§7Next MF at: §f$it" })); sy += 12
        stat(c, area.x + 9, sy, "Total Candies Used:", "${pets.sumOf { it.candyUsed }}", emptyList()); sy += 12
        val xp = pets.sumOf { it.xp }
        stat(c, area.x + 9, sy, "Total Pet XP:", fmt(xp), listOf("§f${full(xp)} XP"))
        y += STATS_H + 8

        // Active pet
        var cy = c.card(area.x, y, area.w, ACTIVE_H, "Active Pet")
        val ap = c.member.activePet
        if (ap == null) c.text("No active pet", area.x + 9, cy + 6, t.mut, PvCtx.S_SM)
        else {
            tile(c, ap, area.x + 9, cy)
            val tx = area.x + 9 + T + 6
            val held = ap.heldItem?.let { "§7Held: §a" + PvData.pretty(it.removePrefix("PET_ITEM_")) } ?: "§7No held item"
            c.legacy("${ap.rarityCode}${PvData.pretty(ap.tier)} ${ap.name}  $held", tx, cy + 1, PvCtx.S_MD)
            c.text("Level ${ap.level.level}", tx, cy + 11, t.mut, PvCtx.S_SM)
            c.tip(tx, cy, area.w - T - 24, T, tipLines(ap))
        }
        y += ACTIVE_H + 8

        // Other pets
        val list = others(c)
        cy = c.card(area.x, y, area.w, 21 + gridH(list.size, area.w) + 8, "Other Pets")
        c.text("· ${list.size}", area.x + 9 + c.textW("Other Pets", PvCtx.S_LG) + 4, y + 8, t.mut, PvCtx.S_SM)
        if (list.isEmpty()) { c.text("None", area.x + 9, cy, t.mut, PvCtx.S_SM); return }
        val n = cols(area.w)
        for ((i, p) in list.withIndex()) {
            val x = area.x + 9 + (i % n) * (T + GX); val py = cy + (i / n) * CELL_H
            tile(c, p, x, py)
            val lbl = "LVL ${p.level.level}"
            c.text(lbl, x + (T - c.textW(lbl, PvCtx.S_XS)) / 2, py + T + 2, PvTables.RARITY_COLOR[p.tier] ?: t.fg, PvCtx.S_XS)
        }
    }

    private fun tile(c: PvCtx, p: PvPet, x: Int, y: Int) {
        val rc = PvTables.RARITY_COLOR[p.tier] ?: 0xFF888888.toInt()
        PvCtx.smoothRect(c.g, x, y, T, T, 4f, (rc and 0x00FFFFFF) or 0x70000000, c.dp)
        c.stack(PvPets.icon(p), x + 2, y + 2)
        c.tip(x, y, T, T, tipLines(p))
    }

    private fun tipLines(p: PvPet): List<String> {
        val out = ArrayList(PvPets.tooltip(p))
        if (!p.level.maxed) {
            // petLevel(huge) leaves overflow = huge - xpToMax
            val big = 1e12
            val maxXp = big - PvTables.petLevel(big, p.tier, p.type).xpInto
            out += "§7XP to max: §e${fmt((maxXp - p.xp).coerceAtLeast(0.0))}"
        }
        return out
    }

    private fun stat(c: PvCtx, x: Int, y: Int, label: String, v: String, tip: List<String>) {
        c.text(label, x, y, c.theme.mut)
        val vx = x + c.textW(label) + 4
        c.bold(v, vx, y, c.theme.fg)
        val vw = c.textW(v)
        if (tip.isNotEmpty()) { c.rect(vx, y + 9, vw, 1, c.theme.line); c.tip(vx, y - 1, vw, 11, tip) }
    }
}
