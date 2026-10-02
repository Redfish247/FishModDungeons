package fishmod.features.pv.tabs

import fishmod.features.ScreenTheme
import fishmod.features.pv.*

object AccessoriesTab : PvTab {
    override val id = "accessories"
    override val title = "Accessories"

    private const val T = 20
    private const val GAP = 3
    private const val STATS_H = 4 * 12 + 16

    private fun cols(w: Int) = ((w - 18 + GAP) / (T + GAP)).coerceAtLeast(1)
    private fun gridH(n: Int, w: Int) = if (n == 0) 12 else ((n + cols(w) - 1) / cols(w)) * (T + GAP)
    private fun order(l: List<PvAccessory>) = l.sortedWith(compareByDescending<PvAccessory> { PvData.RARITY_ORDER.indexOf(it.item.rarity ?: "") }.thenBy { it.item.id ?: "" })

    override fun height(c: PvCtx, area: PvRect): Int {
        val a = c.member.accessories.list
        return STATS_H + 8 + 21 + 26 + gridH(a.count { it.active }, area.w) + 8 + 8 + 21 + gridH(a.count { !it.active }, area.w) + 8
    }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme; val acc = c.member.accessories; val raw = c.member.raw
        val active = order(acc.list.filter { it.active }); val inactive = order(acc.list.filter { !it.active })
        var y = area.y

        // Stats block
        c.card(area.x, y, area.w, STATS_H)
        var sy = y + 8
        val total = acc.list.size
        val pct = if (total == 0) 0 else active.size * 100 / total
        stat(c, area.x + 9, sy, "Unique Accessories:", "${active.size} / $total ($pct%)",
            listOf("§fUnique accessories", "§7Active: §a${active.size}", "§7Duplicates: §c${inactive.size}")); sy += 12
        stat(c, area.x + 9, sy, "Recombobulated:", "${acc.recombCount} / $total", emptyList()); sy += 12
        val tun = acc.tuning.filterValues { it > 0 }
        stat(c, area.x + 9, sy, "Selected Power:", acc.selectedPower?.let { PvData.pretty(it) } ?: "None",
            listOf("§fTuning Points") + (if (tun.isEmpty()) listOf("§7None assigned") else tun.map { (k, v) -> "§7${PvData.pretty(k)}: §a+$v" }) +
                listOf("", "§7Unlocked powers: §f${acc.unlockedPowers.size}")); sy += 12
        val fromRarity = acc.magicalPower
        val abiphone = (raw.arr("nether_island_player_data", "abiphone", "active_contacts")?.size() ?: 0) / 2
        val prism = if (raw.bool("rift", "access", "consumed_prism") == true) 11 else 0
        val mp = acc.highestMagicalPower ?: (fromRarity + abiphone + prism)
        stat(c, area.x + 9, sy, "Magical Power:", full(mp.toDouble()), listOfNotNull(
            "§fMagical Power: §b$mp", "§7From rarities: §f$fromRarity", "§7Abiphone contacts: §f$abiphone", "§7Rift Prism: §f$prism",
            acc.bagUpgrades?.let { "§7Bag upgrades: §f$it" }, acc.highestMagicalPower?.let { "§8(highest recorded by Hypixel)" },
        ))
        y += STATS_H + 8

        // Active
        val ah = 21 + 26 + gridH(active.size, area.w) + 8
        var cy = c.card(area.x, y, area.w, ah, "Active Accessories")
        val enr = acc.list.mapNotNull { it.item.enrichment }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val enrTxt = if (enr.isEmpty()) "§7None" else enr.joinToString("§7, ") { "§b${it.value}x ${PvData.pretty(it.key)}" }
        val ew = c.legacy("§7Enrichments: $enrTxt", area.x + 9, cy, PvCtx.S_SM)
        c.tip(area.x + 9, cy - 1, ew, 10, listOf("§fEnrichments", "§7Enriched: §f${acc.enrichedCount} / $total") + enr.map { "§7${PvData.pretty(it.key)}: §b${it.value}" })
        cy += 11
        val bonus = if (tun.isEmpty()) "§7None" else tun.entries.joinToString(" §8// ") { "§a+${it.value} ${PvData.pretty(it.key)}" }
        c.legacy("§7Bonus: $bonus", area.x + 9, cy, PvCtx.S_SM)
        cy += 15
        grid(c, area.x + 9, cy, area.w, active)
        y += ah + 8

        // Inactive
        val ih = 21 + gridH(inactive.size, area.w) + 8
        cy = c.card(area.x, y, area.w, ih, "Inactive Accessories")
        c.text("· ${inactive.size}", area.x + 9 + c.textW("Inactive Accessories", PvCtx.S_LG) + 4, y + 8, t.mut, PvCtx.S_SM)
        if (inactive.isEmpty()) c.text("None", area.x + 9, cy, t.mut, PvCtx.S_SM) else grid(c, area.x + 9, cy, area.w, inactive)
    }

    private fun stat(c: PvCtx, x: Int, y: Int, label: String, v: String, tip: List<String>) {
        c.text(label, x, y, c.theme.mut)
        val vx = x + c.textW(label) + 4
        c.bold(v, vx, y, c.theme.fg)
        val vw = c.textW(v)
        if (tip.isNotEmpty()) { c.rect(vx, y + 9, vw, 1, c.theme.line); c.tip(vx, y - 1, vw, 11, tip) }
    }

    private fun grid(c: PvCtx, x0: Int, y0: Int, w: Int, list: List<PvAccessory>) {
        val n = cols(w)
        for ((i, a) in list.withIndex()) {
            val x = x0 + (i % n) * (T + GAP); val y = y0 + (i / n) * (T + GAP)
            val rc = PvTables.RARITY_COLOR[a.item.rarity] ?: 0xFF888888.toInt()
            ScreenTheme.roundedRect(c.g, x, y, T, T, 4, (rc and 0x00FFFFFF) or 0x70000000)
            c.stack(a.item.stack, x + 2, y + 2)
            if (a.item.recombobulated) c.rect(x + T - 5, y, 5, 5, 0xFFAA00FF.toInt(), 1.5f)
            val it = a.item
            c.tip(x, y, T, T, listOfNotNull(it.name, "${PvTables.RARITY_CODE[it.rarity] ?: "§f"}§l${(it.rarity ?: "UNKNOWN").replace('_', ' ')}",
                if (it.recombobulated) "§dRecombobulated" else null,
                it.enrichment?.let { e -> "§7Enrichment: §b${PvData.pretty(e)}" },
                if (a.active) "§7Magical Power: §b${a.mp}" else "§cInactive (duplicate)"))
        }
    }
}
