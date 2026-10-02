package fishmod.features.pv.tabs

import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CrimsonIsleTab : PvTab {
    override val id = "crimson"
    override val title = "Crimson Isle"

    private const val TW = 104
    private const val TH = 42
    private const val LINE = 11
    // (api key, name, icon)
    private val DOJO by lazy {
        listOf(Triple("wall_jump", "Stamina", ItemStack(Items.COOKED_CHICKEN)), Triple("archer", "Mastery", ItemStack(Items.BOW)),
            Triple("sword_swap", "Discipline", ItemStack(Items.DIAMOND_SWORD)), Triple("snake", "Swiftness", ItemStack(Items.LEAD)),
            Triple("lock_head", "Control", ItemStack(Items.ENDER_EYE)), Triple("fireball", "Tenacity", ItemStack(Items.FIRE_CHARGE)),
            Triple("mob_kb", "Force", ItemStack(Items.STICK)))
    }
    private val KUUDRA = listOf("none" to "Basic", "hot" to "Hot", "burning" to "Burning", "fiery" to "Fiery", "infernal" to "Infernal")
    private val BELTS = listOf(7000 to "§0Black", 6000 to "§6Brown", 4000 to "§9Blue", 2000 to "§aGreen", 1000 to "§eYellow", 0 to "§fWhite")

    private fun tilesPerRow(w: Int) = ((w - 18 + 6) / (TW + 6)).coerceAtLeast(1)
    private fun dojoH(w: Int) = 21 + 14 + ((DOJO.size + tilesPerRow(w) - 1) / tilesPerRow(w)) * (TH + 6) + 4
    private val sideH get() = 21 + (KUUDRA.size + 1) * LINE + 8

    override fun height(c: PvCtx, area: PvRect): Int = if (c.member.crimson == null) 40 else dojoH(area.w) + 10 + sideH

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        val cr = c.member.crimson
        if (cr == null) { c.text("No Crimson Isle data on this profile", area.x, area.y + 4, t.mut, PvCtx.S_MD); return }
        var y = area.y

        // Dojo
        val dh = dojoH(area.w)
        var cy = c.card(area.x, y, area.w, dh, "Dojo Completions")
        val total = DOJO.sumOf { cr.dojoPoints[it.first] ?: 0 }
        val belt = BELTS.first { total >= it.first }.second
        val tw = c.legacy("Total Points: ${full(total.toDouble())}", area.x + 9, cy, PvCtx.S_MD, t.gold)
        val bw = c.legacy("§7· $belt Belt", area.x + 9 + tw + 4, cy, PvCtx.S_SM)
        c.tip(area.x + 9, cy - 1, tw + bw + 4, LINE, listOf("§fDojo belt: $belt", "§7Belt from total dojo points") +
            BELTS.reversed().map { (p, n) -> "§7$n§7: §f${full(p.toDouble())}+" })
        cy += 14
        val per = tilesPerRow(area.w)
        for ((i, d) in DOJO.withIndex()) {
            val tx = area.x + 9 + (i % per) * (TW + 6)
            val ty = cy + (i / per) * (TH + 6)
            val pts = cr.dojoPoints[d.first]
            val ms = cr.dojoTimes[d.first]
            val rk = rank(pts)
            c.panel(tx, ty, TW, TH, 6, t.panel2)
            c.stack(d.third, tx + 4, ty + (TH - 16) / 2)
            c.bold(d.second, tx + 24, ty + 4, if (rk == "S") t.gold else t.fg, PvCtx.S_MD)
            c.legacy("§7Points: §f${pts?.let { full(it.toDouble()) } ?: "-"}", tx + 24, ty + 14, PvCtx.S_XS)
            c.legacy("§7Rank: §f$rk", tx + 24, ty + 22, PvCtx.S_XS)
            c.legacy("§7Time: §f${time(ms)}", tx + 24, ty + 30, PvCtx.S_XS)
            c.tip(tx, ty, TW, TH, listOf("§f${d.second}", "§7Best points: §f${full((pts ?: 0).toDouble())}", "§7Rank: §f$rk",
                "§7Best time: §f${time(ms)}", "§7Belt contribution: §f${(pts ?: 0) / 10}"))
        }
        y += dh + 10

        // Kuudra | Faction
        val hw = (area.w - 10) / 2
        cy = c.card(area.x, y, hw, sideH, "Kuudra")
        var kt = 0
        for ((k, n) in KUUDRA) {
            val v = cr.kuudra[k] ?: 0
            kt += v
            kv(c, area.x, cy, hw, n, full(v.toDouble()))
            val wave = c.member.raw.obj("nether_island_player_data", "kuudra_completed_tiers").int("highest_wave_$k")
            c.tip(area.x + 9, cy - 1, hw - 18, LINE, listOfNotNull("§f$n Kuudra", "§7Completions: §f$v", wave?.let { "§7Highest wave: §f$it" }))
            cy += LINE
        }
        c.rect(area.x + 9, cy - 2, hw - 18, 1, t.line)
        kv(c, area.x, cy, hw, "§lTotal", "§l" + full(kt.toDouble()))

        val fx = area.x + hw + 10
        cy = c.card(fx, y, hw, sideH, "Crimson Isle")
        val fac = cr.faction?.let { PvData.pretty(it) } ?: "None"
        val facCol = when (cr.faction) { "mages" -> "§5"; "barbarians" -> "§c"; else -> "§f" }
        kv(c, fx, cy, hw, "Faction", facCol + fac); cy += LINE
        val rep = if (cr.faction == "barbarians") cr.barbRep else cr.mageRep
        kv(c, fx, cy, hw, "Reputation", full((rep ?: 0).toDouble()))
        c.tip(fx + 9, cy - 1, hw - 18, LINE, listOf("§fReputation", "§5Mages: §f${full((cr.mageRep ?: 0).toDouble())}",
            "§cBarbarians: §f${full((cr.barbRep ?: 0).toDouble())}"))
        cy += LINE
        kv(c, fx, cy, hw, "Dojo points", full(total.toDouble())); cy += LINE
        kv(c, fx, cy, hw, "Kuudra runs", full(kt.toDouble()))
    }

    private fun kv(c: PvCtx, x: Int, y: Int, w: Int, k: String, v: String) {
        c.legacy(k, x + 9, y, PvCtx.S_MD, c.theme.mut)
        c.legacy(v, x + w - 9 - c.textW(v), y, PvCtx.S_MD, c.theme.fg)
    }

    private fun rank(p: Int?) = when {
        p == null -> "-"; p >= 1000 -> "S"; p >= 800 -> "A"; p >= 600 -> "B"; p >= 400 -> "C"; p >= 200 -> "D"; else -> "F"
    }

    private fun time(ms: Int?): String = if (ms == null || ms <= 0) "-" else "%.1fs".format(ms / 1000.0)
}
