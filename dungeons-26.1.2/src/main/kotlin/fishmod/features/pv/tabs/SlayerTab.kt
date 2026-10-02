package fishmod.features.pv.tabs

import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object SlayerTab : PvTab {
    override val id = "slayer"
    override val title = "Slayer"

    private const val LINE = 10
    private val ICONS by lazy {
        mapOf("zombie" to ItemStack(Items.ROTTEN_FLESH), "spider" to ItemStack(Items.COBWEB), "wolf" to ItemStack(Items.MUTTON),
            "enderman" to ItemStack(Items.ENDER_PEARL), "blaze" to ItemStack(Items.BLAZE_POWDER), "vampire" to ItemStack(Items.REDSTONE))
    }
    private val NAMES = mapOf("zombie" to "Revenant", "spider" to "Tarantula", "wolf" to "Sven", "enderman" to "Voidgloom",
        "blaze" to "Inferno", "vampire" to "Riftstalker")

    private fun cols(w: Int) = if (w >= 560) 3 else 2
    private fun cardH(s: PvSlayer) = 8 + 22 + (s.def.tiers + 1) * LINE + 8

    override fun height(c: PvCtx, area: PvRect): Int {
        val cs = cols(area.w)
        return 18 + c.member.slayers.chunked(cs).sumOf { row -> row.maxOf { cardH(it) } + 10 }
    }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        var y = area.y
        val total = c.member.slayers.sumOf { it.xp }
        c.legacy("§7Total slayer XP: §f§l${full(total.toDouble())}", area.x, y + 2, PvCtx.S_MD)
        y += 18
        val cs = cols(area.w)
        val cw = (area.w - (cs - 1) * 10) / cs
        for (row in c.member.slayers.chunked(cs)) {
            val rh = row.maxOf { cardH(it) }
            for ((i, s) in row.withIndex()) {
                val x = area.x + i * (cw + 10)
                c.card(x, y, cw, rh)
                c.levelRow(x + 9, y + 8, cw - 18, ICONS[s.def.key], NAMES[s.def.key] ?: s.def.name, level(s), listOf("§7${s.def.name}"))
                var ty = y + 8 + 24
                for (tier in 0 until s.def.tiers) {
                    val k = s.tierKills.getOrElse(tier) { 0 }
                    c.text("Tier ${tier + 1}", x + 9, ty, t.mut, PvCtx.S_MD)
                    val v = full(k.toDouble())
                    c.text(v, x + cw - 9 - c.textW(v), ty, t.fg, PvCtx.S_MD)
                    ty += LINE
                }
                c.rect(x + 9, ty - 1, cw - 18, 1, t.line)
                c.bold("Total", x + 9, ty + 1, t.fg, PvCtx.S_MD)
                val tv = full(s.totalKills.toDouble())
                c.bold(tv, x + cw - 9 - c.textW(tv), ty + 1, t.fg, PvCtx.S_MD)
            }
            y += rh + 10
        }
    }

    private fun level(s: PvSlayer): PvTables.Level {
        val th = s.def.thresholds
        val lvl = s.level
        if (lvl >= th.size) return PvTables.Level(lvl, 1.0, s.xp - th.last(), 0, true, th.size, s.xp)
        val prev = if (lvl == 0) 0L else th[lvl - 1]
        val need = th[lvl] - prev
        val into = s.xp - prev
        return PvTables.Level(lvl, into.toDouble() / need, into, need, false, th.size, s.xp)
    }
}
