package fishmod.features.pv.tabs

import fishmod.features.croesus.CroesusPrices
import fishmod.features.pv.*
import fishmod.features.pv.tabs.inv.SackTable
import fishmod.features.ScreenTheme
import net.minecraft.world.item.ItemStack

object InventoryTab : PvTab {
    override val id = "inventory"
    override val title = "Inventory"
    override val subTabs = listOf("Inventory", "Wardrobe", "Weapons", "Ender Chest", "Backpacks", "Sacks", "Personal Vault", "Potion Bag", "Fishing Bag", "Quiver")

    private const val S = PvCtx.SLOT

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
            3 -> enderChest(c, a, d)
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

    private fun weapons(c: PvCtx, a: PvRect, d: Boolean): Int {
        val m = c.member
        val list = cache(c).weapons
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


    // --- per-member cache (no per-frame allocation) ---
    private class SackEntry(val id: String, val count: Long, val price: Double?, val label: String)
    private class SackGroup(val name: String, val list: List<SackEntry>, val value: Double, val header: String)
    private class Cache(val member: PvMember) {
        val weapons: List<PvItem> = (member.inventory.items + member.enderChest.items + member.backpacks.values.flatMap { it.items } + member.vault.items)
            .filterNotNull().filter { isWeapon(it) }.sortedByDescending { PvData.RARITY_ORDER.indexOf(it.rarity ?: "") }
        val enderPages: List<List<PvItem?>> = (0 until member.enderPages).map { member.enderPage(it) }
        val bpKeys: List<Int> = member.backpacks.keys.sorted()
        val bpPages: List<List<PvItem?>> = bpKeys.map { member.backpacks[it]!!.items }
        val wdPages: List<List<PvItem?>> = run {
            val w = member.wardrobe.items
            val n = (w.size + 35) / 36
            val eq = (member.wardrobeEquipped ?: -1) - 1
            (0 until n).map { p ->
                (0 until 36).map { i ->
                    val it = w.getOrNull(p * 36 + i)
                    // worn set lives in inv_armor, not the wardrobe slot
                    if (it == null && eq >= 0 && eq / 9 == p && i % 9 == eq % 9) member.armor.getOrNull(i / 9) else it
                }
            }
        }
        var sacks: List<SackGroup>? = null
        var sackTotal = 0L
        var sackValue = 0.0
    }
    private var cache: Cache? = null
    private fun cache(c: PvCtx): Cache = cache?.takeIf { it.member === c.member } ?: Cache(c.member).also { cache = it }

    // --- full-area pager ---
    private fun enderChest(c: PvCtx, a: PvRect, d: Boolean): Int {
        val k = cache(c)
        if (k.enderPages.isEmpty()) return empty(c, a, d, "Ender Chest", "Nothing here")
        return pager(c, a, d, "Ender Chest", k.enderPages, 9, 5, null, null, null)
    }

    private fun backpacks(c: PvCtx, a: PvRect, d: Boolean): Int {
        val k = cache(c); val m = c.member
        if (k.bpKeys.isEmpty()) return empty(c, a, d, "Backpacks", "No backpacks")
        val rows = k.bpPages.maxOf { (it.size + 8) / 9 }.coerceAtLeast(1)
        val p = pageOf(c, k.bpKeys.size)
        return pager(c, a, d, "Backpack ${k.bpKeys[p] + 1}", k.bpPages, 9, rows, m.backpackIcons[k.bpKeys[p]],
            { i -> m.backpackIcons[k.bpKeys[i]] }, { i -> (k.bpKeys[i] + 1).toString() })
    }

    private fun wardrobe(c: PvCtx, a: PvRect, d: Boolean): Int {
        val k = cache(c); val m = c.member; val t = c.theme
        if (k.wdPages.isEmpty()) {
            // Hypixel omitted wardrobe_contents: show the worn set at least
            val h = 21 + 4 * S + 8
            if (d) {
                val cy = c.card(a.x, a.y, a.w, h, "Wardrobe")
                for (i in 0 until 4) c.item(m.armor.getOrNull(i), a.x + 9, cy + i * S)
                val tx = a.x + 9 + S + 8
                val eqs = m.wardrobeEquipped
                c.text(if (eqs != null && eqs > 0) "Equipped: slot $eqs" else "No wardrobe slot equipped", tx, cy + 2, t.fg, PvCtx.S_MD)
                c.text("Hypixel's API didn't return wardrobe contents", tx, cy + 14, t.mut, PvCtx.S_SM)
                c.text("for this profile, only the worn armor.", tx, cy + 24, t.mut, PvCtx.S_SM)
            }
            return h
        }
        val eq = (m.wardrobeEquipped ?: -1) - 1
        return pager(c, a, d, "Wardrobe", k.wdPages, 9, 4, null, null, null) { p, gx, gy, s ->
            if (eq >= 0 && eq / 9 == p) c.panel(gx + (eq % 9) * s - 1, gy - 1, s + 2, 4 * s + 2, 4, t.acc)
        }
    }

    private fun pager(
        c: PvCtx, a: PvRect, d: Boolean, label: String, pgs: List<List<PvItem?>>, cols: Int, rows: Int, icon: PvItem?,
        btnIcon: ((Int) -> PvItem?)?, btnLabel: ((Int) -> String)?, under: ((Int, Int, Int, Int) -> Unit)? = null,
    ): Int {
        val t = c.theme
        val n = pgs.size
        val p = pageOf(c, n)
        val iconBtns = btnIcon != null
        val bw = if (iconBtns) 22 else 18; val bh = if (iconBtns) 30 else 15; val gap = 3
        val perRow = ((a.w - 18 + gap) / (bw + gap)).coerceAtLeast(1)
        val btnRows = if (n > 1) (n + perRow - 1) / perRow else 0
        val footH = if (btnRows > 0) btnRows * (bh + gap) + 6 else 0
        val headH = 24
        val availW = a.w - 2 * 26
        val availH = a.h - headH - footH - 10
        val s = minOf(availW / cols, availH / rows).coerceIn(S, 48)
        val h = headH + rows * s + 8 + footH
        if (!d) return h
        c.card(a.x, a.y, a.w, h, null)
        var hx = a.x + 9
        if (icon != null) { c.stack(icon.stack, a.x + 7, a.y + 4); c.tip(a.x + 6, a.y + 3, 18, 18, icon.tooltip); hx += 18 }
        c.bold(label, hx, a.y + 8, t.fg, PvCtx.S_LG)
        val pl = "page ${p + 1} of $n"
        c.text(pl, a.right - 9 - c.textW(pl, PvCtx.S_SM), a.y + 9, t.mut, PvCtx.S_SM)
        val gw = cols * s
        val gx = a.x + (a.w - gw) / 2
        val gy = a.y + headH
        under?.invoke(p, gx, gy, s)
        val items = pgs[p]
        for (i in 0 until rows * cols) bigItem(c, items.getOrNull(i), gx + (i % cols) * s, gy + (i / cols) * s, s)
        if (n > 1) {
            val ay = gy + rows * s / 2 - 7
            arrow(c, gx - 20, ay, "‹") { setPage(c, (p - 1 + n) % n) }
            arrow(c, gx + gw + 6, ay, "›") { setPage(c, (p + 1) % n) }
            var by = gy + rows * s + 8
            for (r in 0 until btnRows) {
                val cnt = minOf(perRow, n - r * perRow)
                var bx = a.x + (a.w - (cnt * (bw + gap) - gap)) / 2
                for (j in 0 until cnt) {
                    val i = r * perRow + j
                    pageBtn(c, bx, by, bw, bh, i, i == p, btnIcon?.invoke(i), btnLabel?.invoke(i) ?: (i + 1).toString())
                    bx += bw + gap
                }
                by += bh + gap
            }
        }
        return h
    }

    private fun pageBtn(c: PvCtx, x: Int, y: Int, w: Int, h: Int, i: Int, on: Boolean, icon: PvItem?, label: String) {
        val t = c.theme
        val hov = c.hovered(x, y, w, h)
        val fill = if (on) t.acc else if (hov) t.panel else t.panel2
        val ink = if (on) t.accInk else if (hov) t.fg else t.mut
        if (icon != null || h > 20) {
            c.panel(x, y, w, h, 4, fill, if (on) t.acc else t.line)
            if (icon != null) c.stack(icon.stack, x + (w - 16) / 2, y + 2)
            c.text(label, x + (w - c.textW(label, PvCtx.S_SM)) / 2, y + h - 9, ink, PvCtx.S_SM)
            if (icon != null) c.tip(x, y, w, h, icon.tooltip)
        } else {
            c.ring(x, y, w, h, 4f, fill, if (on) t.acc else t.line)
            c.text(label, x + (w - c.textW(label, PvCtx.S_SM)) / 2, y + (h - 6) / 2, ink, PvCtx.S_SM)
        }
        c.hit(x, y, w, h) { setPage(c, i) }
    }

    // Scaled slot (vanilla layer).
    private fun bigItem(c: PvCtx, it: PvItem?, x: Int, y: Int, s: Int) {
        if (s <= S) { c.item(it, x, y); return }
        val g = c.g
        val pad = maxOf(1, s / 16)
        ScreenTheme.roundedRect(g, x + pad / 2, y + pad / 2, s - pad, s - pad, 3, c.theme.slot)
        if (it == null) return
        val k = (s - 2 * pad - 2) / 16f
        val o = (s - 16 * k) / 2f
        g.pose().pushMatrix()
        g.pose().translate(x + o, y + o)
        g.pose().scale(k, k)
        g.item(it.stack, 0, 0)
        if (it.count > 1) g.itemDecorations(c.font, it.stack, 0, 0)
        g.pose().popMatrix()
        c.tip(x, y, s, s, it.tooltip)
    }

    private fun arrow(c: PvCtx, x: Int, y: Int, s: String, action: () -> Unit) {
        val t = c.theme
        val hov = c.hovered(x, y, 14, 14)
        c.ring(x, y, 14, 14, 7f, if (hov) t.panel else t.panel2, t.line)
        c.bold(s, x + (14 - c.textW(s, PvCtx.S_LG)) / 2, y + 3, if (hov) t.fg else t.mut, PvCtx.S_LG)
        c.hit(x, y, 14, 14, action)
    }

    private val pages = HashMap<Int, Int>()
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
        if (d) { val cy = c.card(a.x, a.y, a.w, 40, label); c.text(msg, a.x + 9, cy + 2, c.theme.mut, PvCtx.S_SM) }
        return 40
    }

    // --- Sacks ---
    private fun sackGroups(c: PvCtx): Cache {
        val k = cache(c)
        if (k.sacks != null) return k
        val by = HashMap<String, ArrayList<SackEntry>>()
        for ((id, n) in c.member.sacks) if (n > 0) {
            val price = CroesusPrices.bazaarPrice(id, false)
            by.getOrPut(SackTable.sackOf(id)) { ArrayList() } += SackEntry(id, n, price, if (n >= 10_000) fmt(n.toDouble()) else n.toString())
        }
        val groups = SackTable.ORDER.mapNotNull { name ->
            val l = by[name]?.sortedByDescending { it.count } ?: return@mapNotNull null
            val v = l.sumOf { (it.price ?: 0.0) * it.count }
            SackGroup(name, l, v, "${l.size} types · ${fmt(l.sumOf { it.count }.toDouble())}" + if (v > 0) " · ${fmt(v)} coins" else "")
        }
        k.sacks = groups
        k.sackTotal = groups.sumOf { g -> g.list.sumOf { it.count } }
        k.sackValue = groups.sumOf { it.value }
        return k
    }

    private const val CW = 30
    private const val CH = 31

    private fun sacks(c: PvCtx, a: PvRect, d: Boolean): Int {
        val t = c.theme
        val k = sackGroups(c)
        val groups = k.sacks ?: emptyList()
        if (groups.isEmpty()) return empty(c, a, d, "Sacks", "No sack items (or API disabled)")
        var y = a.y
        if (d) {
            var x = a.x
            val w1 = c.legacy("Items in sacks: §f${full(k.sackTotal.toDouble())}", x, y + 2, PvCtx.S_MD, t.mut); x += w1 + 16
            val w2 = c.legacy("Sack value: §6${if (k.sackValue > 0) fmt(k.sackValue) else "?"}", x, y + 2, PvCtx.S_MD, t.mut)
            c.tip(x, y, w2, 12, listOf("§fInstant-sell bazaar value", if (k.sackValue > 0) "§6${full(k.sackValue)} coins" else "§7Bazaar prices not loaded"))
        }
        y += 18
        val cols = ((a.w - 18) / CW).coerceAtLeast(1)
        for (g in groups) {
            val rows = (g.list.size + cols - 1) / cols
            val h = 24 + rows * CH + 6
            if (d) {
                c.card(a.x, y, a.w, h, null)
                val title = "${g.name} Sack"
                c.bold(title, a.x + 9, y + 8, t.fg, PvCtx.S_LG)
                val hw = c.textW(g.header, PvCtx.S_SM)
                if (a.x + 9 + c.textW(title, PvCtx.S_LG) + 10 < a.right - 9 - hw)
                    c.text(g.header, a.right - 9 - hw, y + 9, t.mut, PvCtx.S_SM)
                val gx0 = a.x + 9 + ((a.w - 18) - cols * CW) / 2
                val gy0 = y + 24
                for ((i, e) in g.list.withIndex()) {
                    val sx = gx0 + (i % cols) * CW; val sy = gy0 + (i / cols) * CH
                    val bx = sx + (CW - S) / 2
                    c.panel(bx, sy, S, S, 3, t.slot)
                    c.stack(SackTable.icon(e.id), bx + 1, sy + 1)
                    c.text(e.label, sx + (CW - c.textW(e.label, PvCtx.S_XS)) / 2, sy + S + 2, t.fg, PvCtx.S_XS)
                    if (c.hovered(sx, sy, CW, CH)) {
                        val lines = arrayListOf("§f${SackTable.name(e.id)}", "§7Stored: §f${full(e.count.toDouble())}")
                        if (e.price != null) { lines += "§7Bazaar: §6${"%,.1f".format(e.price)} §7each"; lines += "§7Value: §6${fmt(e.price * e.count)}" }
                        c.screen.setTip(lines)
                    }
                }
            }
            y += h + 8
        }
        return y - a.y
    }
}
