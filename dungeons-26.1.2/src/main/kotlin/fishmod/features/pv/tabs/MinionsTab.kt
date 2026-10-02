package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.croesus.LootIcons
import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object MinionsTab : PvTab {
    override val id = "minions"
    override val title = "Minions"

    private class Minion(val key: String, val name: String, val max: Int)

    private val XII = setOf(
        "WHEAT", "CARROT", "POTATO", "PUMPKIN", "MELON", "SUGAR_CANE", "COCOA", "CACTUS", "NETHER_WARTS", "MUSHROOM",
        "CHICKEN", "COW", "PIG", "SHEEP", "RABBIT", "COBBLESTONE", "COAL", "IRON", "GOLD", "DIAMOND", "LAPIS", "REDSTONE",
        "EMERALD", "QUARTZ", "SNOW", "CLAY", "FISHING", "SLIME", "MAGMA_CUBE", "ZOMBIE", "SKELETON", "SPIDER", "CREEPER",
        "REVENANT", "OAK", "SPRUCE", "BIRCH", "DARK_OAK", "ACACIA", "JUNGLE", "FLOWER", "GLOWSTONE", "MITHRIL", "HARD_STONE",
    )
    private fun m(vararg keys: String) = keys.map { k ->
        val nm = when (k) { "NETHER_WARTS" -> "Nether Wart"; "CAVESPIDER" -> "Cave Spider"; "ENDER_STONE" -> "End Stone"; else -> PvData.pretty(k) }
        Minion(k, nm, if (k in XII) 12 else 11)
    }

    private val GROUPS: List<Pair<String, List<Minion>>> by lazy {
        listOf(
            "Farming" to m("WHEAT", "CARROT", "POTATO", "PUMPKIN", "MELON", "SUGAR_CANE", "COCOA", "CACTUS", "NETHER_WARTS", "MUSHROOM", "CHICKEN", "COW", "PIG", "SHEEP", "RABBIT", "SUNFLOWER", "MOONFLOWER"),
            "Mining" to m("COBBLESTONE", "COAL", "IRON", "GOLD", "DIAMOND", "LAPIS", "REDSTONE", "EMERALD", "QUARTZ", "OBSIDIAN", "GLOWSTONE", "GRAVEL", "SAND", "RED_SAND", "MYCELIUM", "ENDER_STONE", "ICE", "SNOW", "MITHRIL", "HARD_STONE", "TUNGSTEN", "UMBER"),
            "Combat" to m("ZOMBIE", "SKELETON", "SPIDER", "CAVESPIDER", "CREEPER", "ENDERMAN", "BLAZE", "GHAST", "SLIME", "MAGMA_CUBE", "REVENANT", "TARANTULA", "VOIDLING", "INFERNO", "VAMPIRE"),
            "Foraging" to m("OAK", "SPRUCE", "BIRCH", "DARK_OAK", "ACACIA", "JUNGLE", "FLOWER"),
            "Fishing" to m("FISHING", "CLAY"),
        )
    }
    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    // Unique crafts needed for each slot above the base 5.
    private val SLOT_REQ = intArrayOf(5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225, 250, 275, 300, 350, 400, 450, 500, 550, 600, 650)
    private val icons = HashMap<String, ItemStack>()
    private val FALLBACK by lazy { ItemStack(Items.SPAWNER) }

    private fun icon(key: String): ItemStack = icons[key] ?: (LootIcons.icon("${key}_GENERATOR_1")?.also { icons[key] = it } ?: FALLBACK)

    private fun merged(c: PvCtx): Map<String, Set<Int>> {
        val out = HashMap<String, MutableSet<Int>>()
        for (mem in c.profile.members.values) for ((k, v) in mem.minions) out.getOrPut(k) { HashSet() }.addAll(v)
        return out
    }

    private fun groups(c: PvCtx, data: Map<String, Set<Int>>): List<Pair<String, List<Minion>>> {
        val known = GROUPS.flatMap { g -> g.second.map { it.key } }.toSet()
        val other = data.keys.filter { it !in known }.sorted().map { Minion(it, PvData.pretty(it), 11) }
        return if (other.isEmpty()) GROUPS else GROUPS + ("Other" to other)
    }

    private fun chipW(c: PvCtx, mn: Minion) = 22 + c.textW(mn.name, PvCtx.S_SM) + 4 + c.textW("XII", PvCtx.S_SM) + 6

    private fun layout(c: PvCtx, area: PvRect, draw: Boolean): Int {
        val t = c.theme
        val data = merged(c)
        val unique = data.values.sumOf { it.size }
        var y = area.y
        if (draw) {
            val bonus = communitySlots(c.profile.raw)
            val base = 5 + SLOT_REQ.count { unique >= it }
            val maxed = groups(c, data).sumOf { g -> g.second.count { mn -> (data[mn.key]?.maxOrNull() ?: 0) >= 11 } }
            var x = area.x
            x += stat(c, x, y, "Unique tiers crafted: §f$unique", listOf("§fUnique minion tiers crafted", "§7Across all ${c.profile.members.size} co-op member(s)",
                SLOT_REQ.firstOrNull { it > unique }?.let { "§7Next slot at §f$it §7(${it - unique} more)" } ?: "§6All craft slots unlocked"))
            x += stat(c, x, y, "Minion slots: §f${base + bonus}", listOf("§fMinion slots", "§7From crafts: §f$base", "§7Community upgrades: §f+$bonus"))
            stat(c, x, y, "Maxed: §6$maxed", listOf("§fMinions at tier XI or higher: §6$maxed"))
        }
        y += 16
        for ((cat, list) in groups(c, data)) {
            // chip wrap
            var cx = 0; var rows = 1
            for (mn in list) { val w = chipW(c, mn); if (cx + w > area.w - 18 && cx > 0) { rows++; cx = 0 }; cx += w + 4 }
            val h = 21 + rows * 20 + 6
            if (draw) {
                val tiers = list.sumOf { data[it.key]?.size ?: 0 }
                val total = list.sumOf { it.max }
                val cy0 = c.card(area.x, y, area.w, h, null)
                c.bold(cat, area.x + 9, y + 7, t.fg, PvCtx.S_LG)
                val hdr = "$tiers / $total tiers"
                c.text(hdr, area.right - 9 - c.textW(hdr, PvCtx.S_SM), y + 8, t.mut, PvCtx.S_SM)
                var x = area.x + 9; var yy = cy0 + 13
                for (mn in list) {
                    val w = chipW(c, mn)
                    if (x + w > area.right - 9 && x > area.x + 9) { x = area.x + 9; yy += 20 }
                    chip(c, mn, data[mn.key] ?: emptySet(), x, yy, w)
                    x += w + 4
                }
            }
            y += h + 8
        }
        return y - area.y
    }

    private fun chip(c: PvCtx, mn: Minion, tiers: Set<Int>, x: Int, y: Int, w: Int) {
        val t = c.theme
        val top = tiers.maxOrNull() ?: 0
        val maxed = top >= 11
        val faded = top == 0
        c.panel(x, y, w, 17, 8, if (c.hovered(x, y, w, 17)) t.panel else t.panel2, t.line)
        c.stack(icon(mn.key), x + 2, y + 1)
        val fg = if (faded) t.mut else t.fg
        c.text(mn.name, x + 21, y + 5, fg, PvCtx.S_SM)
        val tl = if (top == 0) "–" else ROMAN.getOrElse(top - 1) { top.toString() }
        c.bold(tl, x + 21 + c.textW(mn.name, PvCtx.S_SM) + 4, y + 5, if (maxed) t.gold else if (faded) t.mut else t.acc, PvCtx.S_SM)
        if (faded) c.rect(x, y, w, 17, 0x55000000, 8f)
        val crafted = (1..mn.max).joinToString(" ") { i -> (if (i in tiers) "§a" else "§8") + ROMAN[i - 1] }
        c.tip(x, y, w, 17, listOf("§f${mn.name} Minion", "§7Highest tier: " + (if (top == 0) "§cnone" else "§f$tl"),
            "§7Tiers crafted: §f${tiers.size} / ${mn.max}", "", crafted))
    }

    private fun stat(c: PvCtx, x: Int, y: Int, label: String, tip: List<String>): Int {
        val w = c.legacy(label, x, y, PvCtx.S_MD, c.theme.mut)
        c.rect(x, y + 9, w, 1, c.theme.line)
        c.tip(x, y - 1, w, 11, tip)
        return w + 16
    }

    private fun communitySlots(raw: JsonObject): Int = runCatching {
        raw.getAsJsonObject("community_upgrades")?.getAsJsonArray("upgrade_states")
            ?.mapNotNull { it as? JsonObject }?.filter { it.get("upgrade")?.asString == "minion_slots" }
            ?.maxOfOrNull { it.get("tier")?.asInt ?: 0 } ?: 0
    }.getOrDefault(0)

    override fun height(c: PvCtx, area: PvRect) = layout(c, area, false)
    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) { layout(c, area, true) }
}
