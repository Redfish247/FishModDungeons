package fishmod.features.pv.tabs

import fishmod.features.croesus.CroesusPrices
import fishmod.features.croesus.LootIcons
import fishmod.features.pv.*
import fishmod.utils.networth.ItemsDb
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object InventoryTab : PvTab {
    override val id = "inventory"
    override val title = "Inventory"
    override val subTabs = listOf("Inventory", "Wardrobe", "Weapons", "Ender Chest", "Backpacks", "Sacks", "Personal Vault", "Potion Bag", "Fishing Bag", "Quiver")

    private const val S = PvCtx.SLOT
    private val pages = HashMap<Int, Int>()

    private val SACKS: List<Pair<String, List<String>>> = listOf(
        "Agronomy" to listOf("WHEAT", "SEEDS", "CARROT_ITEM", "POTATO_ITEM", "POISONOUS_POTATO", "PUMPKIN", "MELON", "SUGAR_CANE", "INK_SACK:3", "CACTUS", "NETHER_STALK", "RED_MUSHROOM", "BROWN_MUSHROOM", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE"),
        "Husbandry" to listOf("RAW_CHICKEN", "FEATHER", "EGG", "RAW_BEEF", "LEATHER", "PORK", "MUTTON", "WOOL", "RABBIT", "RABBIT_FOOT", "RABBIT_HIDE"),
        "Mining" to listOf("COBBLESTONE", "COAL", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "INK_SACK:4", "REDSTONE", "EMERALD", "QUARTZ", "OBSIDIAN", "GLOWSTONE_DUST", "GRAVEL", "FLINT", "SAND", "SAND:1", "ENDER_STONE", "ICE", "SNOW_BALL", "MITHRIL_ORE", "TITANIUM_ORE", "HARD_STONE", "GLACITE", "UMBER", "TUNGSTEN", "SULPHUR", "STARFALL"),
        "Combat" to listOf("ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "SULPHUR", "ENDER_PEARL", "GHAST_TEAR", "SLIME_BALL", "BLAZE_ROD", "MAGMA_CREAM", "REVENANT_FLESH", "TARANTULA_WEB", "NULL_SPHERE", "WOLF_TOOTH"),
        "Foraging" to listOf("LOG", "LOG:1", "LOG:2", "LOG:3", "LOG_2", "LOG_2:1", "MANGROVE_LOG", "FIG_LOG", "CHERRY_LOG"),
        "Fishing" to listOf("RAW_FISH", "RAW_FISH:1", "RAW_FISH:2", "RAW_FISH:3", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "CLAY_BALL", "WATER_LILY", "INK_SACK", "SPONGE"),
        "Gemstone" to emptyList(), // filled by prefix
        "Dragon" to listOf("SUMMONING_EYE", "REMNANT_OF_THE_EYE", "OLD_DRAGON_FRAGMENT", "STRONG_DRAGON_FRAGMENT", "WISE_DRAGON_FRAGMENT", "UNSTABLE_DRAGON_FRAGMENT", "YOUNG_DRAGON_FRAGMENT", "SUPERIOR_DRAGON_FRAGMENT", "PROTECTOR_DRAGON_FRAGMENT", "HOLY_DRAGON_FRAGMENT"),
    )

    override fun height(c: PvCtx, area: PvRect) = layout(c, area, false)
    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) { layout(c, area, true) }

    private fun layout(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        if (!m.inventoryApi && c.sub != 5) {
            if (d) c.text("Inventory API disabled", a.x, a.y + 4, c.theme.bad)
            return 20
        }
        return when (c.sub) {
            0 -> inventory(c, a, d)
            1 -> wardrobe(c, a, d)
            2 -> weapons(c, a, d)
            3 -> carousel(c, a, d, "Ender Chest", (0 until m.enderPages).map { m.enderPage(it) }, 9)
            4 -> backpacks(c, a, d)
            5 -> sacks(c, a, d)
            6 -> simple(c, a, d, "Personal Vault", m.vault)
            7 -> simple(c, a, d, "Potion Bag", m.potionBag)
            8 -> simple(c, a, d, "Fishing Bag", m.fishingBag)
            else -> simple(c, a, d, "Quiver", m.quiver)
        }
    }

    private fun grid(c: PvCtx, items: List<PvItem?>, x: Int, y: Int, cols: Int, slots: Int = items.size): Int {
        val rows = (slots + cols - 1) / cols
        for (i in 0 until slots) c.item(items.getOrNull(i), x + (i % cols) * S, y + (i / cols) * S)
        return rows * S
    }

    private fun inventory(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        val h = 21 + 4 * S + 3 + 8
        val w = 9 * S + 18 + 4 * S + 22
        if (d) {
            val cy = c.card(a.x, a.y, w, h, "Inventory")
            for (i in 0 until 4) c.item(m.armor.getOrNull(i), a.x + 9, cy + i * S)
            for (i in 0 until 4) c.item(m.equipment.items.getOrNull(i), a.x + 9 + S + 2, cy + i * S)
            val gx = a.x + 9 + 2 * S + 12
            for (row in 0 until 4) for (col in 0 until 9) {
                val idx = if (row < 3) 9 + row * 9 + col else col
                c.item(m.inventory.items.getOrNull(idx), gx + col * S, cy + row * S + if (row == 3) 3 else 0)
            }
        }
        return h
    }

    private fun wardrobe(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        val pgs = (0 until m.wardrobe.size / 36).map { p -> m.wardrobe.items.drop(p * 36).take(36) }
        val h = carousel(c, a, d, "Wardrobe", pgs, 9)
        if (d && m.wardrobeEquipped != null) {
            val eq = m.wardrobeEquipped - 1
            if (eq / 9 == pageOf(c, pgs.size)) {
                // equipped column marker
                val gx = a.x + 9 + 20 + (eq % 9) * S
                c.rect(gx + 2, a.y + 21 + 4 * S + 1, S - 4, 2, c.theme.acc, 1f)
            }
        }
        return h
    }

    private fun weapons(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        val all = m.inventory.items + m.enderChest.items + m.backpacks.values.flatMap { it.items } + m.vault.items
        val list = all.filterNotNull().filter { isWeapon(it) }
            .sortedByDescending { PvData.RARITY_ORDER.indexOf(it.rarity ?: "") }
        val cols = ((a.w - 18) / S).coerceIn(1, 18)
        val h = 21 + maxOf(1, (list.size + cols - 1) / cols) * S + 8
        if (d) {
            val cy = c.card(a.x, a.y, a.w, h, "Weapons · ${list.size}")
            if (list.isEmpty()) c.text("No weapons found", a.x + 9, cy + 4, c.theme.mut, PvCtx.S_SM)
            else grid(c, list, a.x + 9, cy, cols)
        }
        return h
    }

    private fun isWeapon(it: PvItem): Boolean {
        val l = it.lore.lastOrNull { s -> s.isNotBlank() }?.replace(Regex("§."), "") ?: return false
        return Regex("\\b(SWORD|BOW|LONGSWORD|FISHING WEAPON)\\b").containsMatchIn(l)
    }

    private fun backpacks(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        val keys = m.backpacks.keys.sorted()
        if (keys.isEmpty()) return empty(c, a, d, "Backpacks", "No backpacks")
        val p = pageOf(c, keys.size)
        val k = keys[p]
        val icon = m.backpackIcons[k]
        val inv = m.backpacks[k]!!
        val h = carousel(c, a, d, "Backpack ${k + 1}", keys.map { m.backpacks[it]!!.items }, 9, icon)
        if (d && icon != null) c.tip(a.x + 6, a.y + 3, 18, 18, icon.tooltip + listOf("", "§7Slots: §f${inv.size}"))
        return h
    }

    // Paged slot grid with arrows + dots.
    private fun carousel(c: PvCtx, a: PvRect, d: Boolean, label: String, pgs: List<List<PvItem?>>, cols: Int, icon: PvItem? = null): Int {
        if (pgs.isEmpty()) return empty(c, a, d, label, "Nothing here")
        val p = pageOf(c, pgs.size)
        val rows = pgs.maxOf { (it.size + cols - 1) / cols }.coerceAtLeast(1)
        val w = cols * S + 18 + 40
        val h = 21 + rows * S + 18 + 6
        if (!d) return h
        val t = c.theme
        c.card(a.x, a.y, w, h, null)
        var hx = a.x + 9
        if (icon != null) { c.stack(icon.stack, a.x + 7, a.y + 4); hx += 18 }
        c.bold(label, hx, a.y + 7, t.fg, PvCtx.S_LG)
        val pl = "page ${p + 1} of ${pgs.size}"
        c.text(pl, a.x + w - 9 - c.textW(pl, PvCtx.S_SM), a.y + 8, t.mut, PvCtx.S_SM)
        val gy = a.y + 21
        grid(c, pgs[p], a.x + 9 + 20, gy, cols, rows * cols)
        val ay = gy + rows * S / 2 - 7
        arrow(c, a.x + 6, ay, "‹") { setPage(c, (p - 1 + pgs.size) % pgs.size) }
        arrow(c, a.x + w - 20, ay, "›") { setPage(c, (p + 1) % pgs.size) }
        // dots
        val dy = gy + rows * S + 8
        val n = pgs.size
        val dx0 = a.x + (w - n * 8) / 2
        for (i in 0 until n) {
            val dx = dx0 + i * 8
            c.rect(dx, dy, if (i == p) 6 else 4, 4, if (i == p) t.acc else t.track, 2f)
            c.hit(dx - 1, dy - 2, 8, 8) { setPage(c, i) }
        }
        return h
    }

    private fun arrow(c: PvCtx, x: Int, y: Int, s: String, action: () -> Unit) {
        val t = c.theme
        val hov = c.hovered(x, y, 14, 14)
        c.ring(x, y, 14, 14, 7f, if (hov) t.panel else t.panel2, t.line)
        c.bold(s, x + (14 - c.textW(s, PvCtx.S_LG)) / 2, y + 3, if (hov) t.fg else t.mut, PvCtx.S_LG)
        c.hit(x, y, 14, 14, action)
    }

    private fun pageOf(c: PvCtx, n: Int): Int = (pages[c.sub] ?: 0).coerceIn(0, maxOf(0, n - 1))
    private fun setPage(c: PvCtx, p: Int) { pages[c.sub] = p }

    private fun simple(c: PvCtx, a: PvRect, d: Boolean, label: String, inv: PvInv): Int {
        if (inv.size == 0) return empty(c, a, d, label, "Not unlocked or empty")
        val cols = 9
        val h = 21 + ((inv.size + cols - 1) / cols) * S + 8
        if (d) { val cy = c.card(a.x, a.y, cols * S + 18, h, label); grid(c, inv.items, a.x + 9, cy, cols) }
        return h
    }

    private fun empty(c: PvCtx, a: PvRect, d: Boolean, label: String, msg: String): Int {
        if (d) { val cy = c.card(a.x, a.y, 9 * S + 18, 40, label); c.text(msg, a.x + 9, cy + 2, c.theme.mut, PvCtx.S_SM) }
        return 40
    }

    // --- Sacks ---
    private class SackEntry(val id: String, val count: Long, val price: Double?)
    private val sackIcons = HashMap<String, ItemStack>()
    private val PAPER by lazy { ItemStack(Items.PAPER) }
    private fun sackIcon(id: String) = sackIcons[id] ?: (LootIcons.icon(id)?.also { sackIcons[id] = it } ?: PAPER)
    private fun itemName(id: String): String = runCatching { ItemsDb.get(id)?.get("name")?.asString }.getOrNull()?.replace(Regex("§."), "") ?: PvData.pretty(id.replace(':', '_'))

    private fun sackGroups(c: PvCtx): List<Pair<String, List<SackEntry>>> {
        val have = c.member.sacks.filterValues { it > 0 }
        val used = HashSet<String>()
        val out = ArrayList<Pair<String, List<SackEntry>>>()
        fun entry(id: String) = SackEntry(id, have[id]!!, CroesusPrices.bazaarPrice(id, false))
        for ((cat, ids) in SACKS) {
            val keys = if (cat == "Gemstone") have.keys.filter { it.endsWith("_GEM") && it !in used } else ids.filter { it in have && it !in used }
            used += keys
            if (keys.isNotEmpty()) out += cat to keys.map(::entry)
        }
        val rest = have.keys.filter { it !in used }.sortedByDescending { have[it] }
        if (rest.isNotEmpty()) out += "Other" to rest.map(::entry)
        return out
    }

    private fun sacks(c: PvCtx, a: PvRect, d: Boolean): Int {
        val t = c.theme
        val groups = sackGroups(c)
        var y = a.y
        if (groups.isEmpty()) return empty(c, a, d, "Sacks", "No sack items (or API disabled)")
        val tot = groups.sumOf { g -> g.second.sumOf { it.count } }
        val value = groups.sumOf { g -> g.second.sumOf { (it.price ?: 0.0) * it.count } }
        if (d) {
            var x = a.x
            val w1 = c.legacy("Items in sacks: §f${full(tot.toDouble())}", x, y, PvCtx.S_MD, t.mut); x += w1 + 16
            val w2 = c.legacy("Sack value: §6${if (value > 0) fmt(value) else "?"}", x, y, PvCtx.S_MD, t.mut)
            c.rect(x, y + 9, w2, 1, t.line)
            c.tip(x, y - 1, w2, 11, listOf("§fInstant-sell bazaar value", if (value > 0) "§6${full(value)} coins" else "§7Bazaar prices not loaded"))
        }
        y += 16
        val cols = ((a.w - 18) / S).coerceIn(1, 24)
        for ((cat, list) in groups) {
            val h = 21 + ((list.size + cols - 1) / cols) * S + 8
            if (d) {
                val cy = c.card(a.x, y, a.w, h, null)
                c.bold("$cat Sack", a.x + 9, y + 7, t.fg, PvCtx.S_LG)
                val sv = list.sumOf { (it.price ?: 0.0) * it.count }
                val hd = "${list.size} types" + if (sv > 0) " · ${fmt(sv)}" else ""
                c.text(hd, a.right - 9 - c.textW(hd, PvCtx.S_SM), y + 8, t.mut, PvCtx.S_SM)
                for ((i, e) in list.withIndex()) {
                    val sx = a.x + 9 + (i % cols) * S; val sy = cy + (i / cols) * S
                    c.panel(sx, sy, S, S, 3, t.slot)
                    c.stack(sackIcon(e.id), sx + 1, sy + 1)
                    val ct = if (e.count >= 1000) fmt(e.count.toDouble()) else e.count.toString()
                    c.text(ct, sx + S - 1 - c.textW(ct, PvCtx.S_XS), sy + S - 7, t.fg, PvCtx.S_XS)
                    val lines = arrayListOf("§f${itemName(e.id)}", "§7Stored: §f${full(e.count.toDouble())}")
                    if (e.price != null) { lines += "§7Bazaar: §6${"%,.1f".format(e.price)} §7each"; lines += "§7Value: §6${fmt(e.price * e.count)}" }
                    c.tip(sx, sy, S, S, lines)
                }
            }
            y += h + 8
        }
        return y - a.y
    }
}
