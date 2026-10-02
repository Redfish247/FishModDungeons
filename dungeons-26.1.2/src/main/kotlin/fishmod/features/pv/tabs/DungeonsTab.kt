package fishmod.features.pv.tabs

import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

object DungeonsTab : PvTab {
    override val id = "dungeons"
    override val title = "Dungeons"

    private const val ROW = 24
    private const val LINE = 11
    private val BOSSES = listOf("Bonzo", "Scarf", "Prof.", "Thorn", "Livid", "Sadan", "Necron")
    private val CLASS_ICONS by lazy {
        mapOf("healer" to ItemStack(Items.POTION), "mage" to ItemStack(Items.BLAZE_ROD), "berserk" to ItemStack(Items.IRON_SWORD),
            "archer" to ItemStack(Items.BOW), "tank" to ItemStack(Items.IRON_CHESTPLATE))
    }
    private val CATA_ICON by lazy { ItemStack(Items.WITHER_SKELETON_SKULL) }
    private val ESSENCE by lazy {
        listOf("WITHER" to ItemStack(Items.WITHER_SKELETON_SKULL), "UNDEAD" to ItemStack(Items.BONE), "DRAGON" to ItemStack(Items.DRAGON_BREATH),
            "GOLD" to ItemStack(Items.GOLD_INGOT), "DIAMOND" to ItemStack(Items.DIAMOND), "ICE" to ItemStack(Items.ICE),
            "SPIDER" to ItemStack(Items.SPIDER_EYE), "CRIMSON" to ItemStack(Items.NETHER_WART))
    }

    private val levelsH get() = 8 + 3 * ROW + 4
    private const val INFO_H = 8 + 5 * LINE + 6
    private const val RUNS_H = 21 + 9 * LINE + 8
    private val ESS_H get() = 21 + ESSENCE.size * 20 + 6

    override fun height(c: PvCtx, area: PvRect): Int =
        if (c.member.dungeons == null) 40 else levelsH + 8 + INFO_H + 8 + max(RUNS_H, ESS_H)

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        val d = c.member.dungeons
        if (d == null) { c.text("No dungeon data on this profile", area.x, area.y + 4, t.mut, PvCtx.S_MD); return }
        var y = area.y

        // Levels: Catacombs + classes, 2 columns
        c.card(area.x, y, area.w, levelsH)
        val gap = 18
        val colW = (area.w - 18 - gap) / 2
        val rows = listOf(Triple(CATA_ICON, "Catacombs", d.cata)) +
            d.classes.map { (k, v) -> Triple(CLASS_ICONS[k], PvData.pretty(k), v) }
        for ((i, r) in rows.withIndex()) {
            val cx = area.x + 9 + (i % 2) * (colW + gap)
            val cy = y + 8 + (i / 2) * ROW
            c.levelRow(cx, cy, colW, r.first, r.second, r.third)
        }
        y += levelsH + 8

        // Info card
        c.card(area.x, y, area.w, INFO_H)
        var iy = y + 8
        val ix = area.x + 9
        var w = c.legacy("§7Selected Class: §f§l${d.selectedClass?.let { PvData.pretty(it) } ?: "None"}", ix, iy, PvCtx.S_MD)
        iy += LINE
        w = c.legacy("Class Average: ${"%.2f".format(d.classAverage)}", ix, iy, PvCtx.S_MD, t.gold)
        c.tip(ix, iy - 1, w, LINE, listOf("§fClass levels") + d.classes.map { (k, v) -> "§7${PvData.pretty(k)}: §f${"%.2f".format(v.fractional)}" })
        iy += LINE
        val hn = d.floors.lastOrNull { it.floor > 0 && it.completions > 0 }
        w = c.legacy("Highest Floor Beaten (Normal): ${hn?.let { "Floor ${it.floor}" } ?: "None"}", ix, iy, PvCtx.S_MD, t.gold)
        hn?.let { c.tip(ix, iy - 1, w, LINE, floorTip(it, BOSSES[it.floor - 1])) }
        iy += LINE
        val hm = d.master.lastOrNull { it.completions > 0 }
        w = c.legacy("Highest Floor Beaten (Master): ${hm?.let { "Floor ${it.floor}" } ?: "None"}", ix, iy, PvCtx.S_MD, t.gold)
        hm?.let { c.tip(ix, iy - 1, w, LINE, floorTip(it, BOSSES[it.floor - 1])) }
        iy += LINE
        val runs = d.totalRuns
        val sr = if (d.secrets != null && runs > 0) " (${"%.2f".format(d.secrets!!.toDouble() / runs)} S/R)" else ""
        w = c.legacy("§7Secrets Found: §f§l${d.secrets?.let { full(it.toDouble()) } ?: "?"}$sr", ix, iy, PvCtx.S_MD)
        c.tip(ix, iy - 1, w, LINE, listOf("§fSecrets", "§7Total: §f${full(d.secrets?.toDouble())}", "§7Runs: §f${full(runs.toDouble())}"))
        y += INFO_H + 8

        // Runs table | Essence list
        val essW = 150
        val runsW = area.w - essW - 12
        runsTable(c, d, area.x, y, runsW)
        essenceCard(c, area.x + runsW + 12, y, essW)
    }

    private fun runsTable(c: PvCtx, d: PvDungeons, x: Int, y: Int, w: Int) {
        val t = c.theme
        var cy = c.card(x, y, w, RUNS_H, "Dungeon Runs")
        val c1 = x + 9; val c2 = x + w / 2; val c3 = x + w * 3 / 4
        c.text("Cata", c2, cy, t.mut, PvCtx.S_SM); c.text("Master", c3, cy, t.mut, PvCtx.S_SM)
        cy += LINE
        for (i in 0 until 7) {
            if (i % 2 == 0) c.rect(x + 4, cy - 2, w - 8, LINE, t.panel2, 3f)
            c.text(BOSSES[i], c1, cy, t.fg, PvCtx.S_MD)
            cell(c, d.floors.firstOrNull { it.floor == i + 1 }, BOSSES[i], c2, cy, w / 4)
            cell(c, d.master.firstOrNull { it.floor == i + 1 }, BOSSES[i], c3, cy, w / 4)
            cy += LINE
        }
        c.rect(x + 9, cy - 2, w - 18, 1, t.line)
        c.bold("Total", c1, cy, t.fg, PvCtx.S_MD)
        c.bold(full(d.floors.filter { it.floor > 0 }.sumOf { it.completions }.toDouble()), c2, cy, t.fg, PvCtx.S_MD)
        c.bold(full(d.master.sumOf { it.completions }.toDouble()), c3, cy, t.fg, PvCtx.S_MD)
    }

    private fun cell(c: PvCtx, f: PvFloor?, boss: String, x: Int, y: Int, w: Int) {
        val n = f?.completions ?: 0
        c.text(full(n.toDouble()), x, y, if (n > 0) c.theme.fg else c.theme.mut, PvCtx.S_MD)
        if (f != null) c.tip(x - 2, y - 2, w, LINE, floorTip(f, boss))
    }

    private fun floorTip(f: PvFloor, boss: String) = listOfNotNull(
        "§f${f.label} · $boss",
        "§7Completions: §f${full(f.completions.toDouble())}",
        f.timesPlayed?.let { "§7Times played: §f${full(it.toDouble())}" },
        "§7Fastest S+: §a${time(f.fastestSPlus)}",
        "§7Fastest S: §e${time(f.fastestS)}",
        f.fastest?.let { "§7Fastest: §f${time(it)}" },
        "§7Best score: §f${f.bestScore ?: "?"}",
        "§7Mobs killed: §f${full(f.mobsKilled?.toDouble() ?: 0.0)}",
        f.mostDamage?.let { "§7Most damage: §c${fmt(it)}" },
    )

    private fun time(ms: Long?): String {
        if (ms == null || ms <= 0) return "§8None"
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun essenceCard(c: PvCtx, x: Int, y: Int, w: Int) {
        val t = c.theme
        var cy = c.card(x, y, w, ESS_H, "Essence")
        for ((k, icon) in ESSENCE) {
            val amt = c.member.essence[k] ?: 0L
            c.panel(x + 9, cy, PvCtx.SLOT, PvCtx.SLOT, 3, t.slot)
            c.stack(icon, x + 10, cy + 1)
            c.text(PvData.pretty(k), x + 32, cy + 5, t.fg, PvCtx.S_MD)
            val s = fmt(amt.toDouble())
            c.bold(s, x + w - 9 - c.textW(s), cy + 5, t.fg, PvCtx.S_MD)
            c.tip(x + 9, cy, w - 18, 18, listOf("§f${PvData.pretty(k)} Essence", "§7${full(amt.toDouble())}"))
            cy += 20
        }
    }
}
