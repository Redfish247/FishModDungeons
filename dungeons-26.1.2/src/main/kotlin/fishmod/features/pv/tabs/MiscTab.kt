package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.croesus.LootIcons
import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

object MiscTab : PvTab {
    override val id = "misc"
    override val title = "Misc"

    private const val GAP = 10
    private const val ROW = 11
    private const val TILE_MIN_W = 132
    private const val TILE_H = 32
    private const val TGAP = 6

    private class Row(val k: String, val v: String, val tip: List<String> = emptyList())
    private class Card(val title: String, val rows: List<Row>) { val h get() = 21 + rows.size * ROW + 5 }
    private class ConsDef(val id: String, val name: String, val max: Int, val fallback: ItemStack, val path: Array<String>)
    private class Cons(val def: ConsDef, val n: Int) {
        val maxed = n >= def.max
        val amount = "${n.coerceAtMost(def.max)} / ${def.max}"
        val tip = listOf("§f${def.name}", "§7Consumed: ${if (maxed) "§6" else "§f"}$amount")
        var shownName = def.name
        var shownW = -1
    }

    // Permanent stat-boost consumables tracked in the member JSON.
    private val CONS_DEFS by lazy {
        listOf(
            ConsDef("REAPER_PEPPER", "Reaper Pepper", 5, ItemStack(Items.RED_DYE), arrayOf("player_data", "reaper_peppers_eaten")),
            ConsDef("ISOPOD_HUSK", "Isopod Husk", 5, ItemStack(Items.PAPER), arrayOf("player_data", "isopod_husks_eaten")),
            ConsDef("BEE_SALIVA", "Bee Saliva", 5, ItemStack(Items.HONEY_BOTTLE), arrayOf("player_data", "bee_saliva_eaten")),
            ConsDef("METAPHYSICAL_SERUM", "Metaphysical Serum", 3, ItemStack(Items.POTION), arrayOf("experimentation", "serums_drank")),
            ConsDef("MCGRUBBER_BURGER", "McGrubber's Burger", 5, ItemStack(Items.COOKED_BEEF), arrayOf("rift", "castle", "grubber_stacks")),
            ConsDef("WRIGGLING_LARVA", "Wriggling Larva", 5, ItemStack(Items.SLIME_BALL), arrayOf("garden_player_data", "larva_consumed")),
            ConsDef("REFINED_BOTTLE_OF_JYRRE", "Refined Bottle of Jyrre", 5, ItemStack(Items.EXPERIENCE_BOTTLE), arrayOf("winter_player_data", "refined_jyrre_uses")),
            ConsDef("REFINED_DARK_CACAO_TRUFFLE", "Refined Dark Cacao Truffle", 5, ItemStack(Items.COCOA_BEANS), arrayOf("events", "easter", "refined_dark_cacao_truffles")),
        )
    }

    private class Built(val cons: List<Cons>, val cards: List<Card>, val kills: List<Pair<String, String>>, val deaths: List<Pair<String, String>>, val totals: String)
    private var builtKey: Any? = null
    private var built: Built? = null
    private var layW = -1
    private var layKey: Any? = null
    private var lay: List<Triple<Card, Int, Int>> = emptyList()
    private var layH = 0

    private fun built(c: PvCtx): Built {
        val m = c.member
        built?.let { if (builtKey === m) return it }
        val ps = m.playerStats
        fun ranked(key: String) = kd(ps, key).mapIndexed { j, e -> "§8#${j + 1} §7${PvData.pretty(e.first)}" to full(e.second) }
        val b = Built(CONS_DEFS.map { Cons(it, m.raw.int(*it.path) ?: 0) }, cards(c), ranked("kills"), ranked("deaths"),
            "Total Kills: §f${full(ps.num("kills", "total") ?: 0.0)}    §7Total Deaths: §f${full(ps.num("deaths", "total") ?: 0.0)}")
        builtKey = m; built = b
        return b
    }

    private fun kd(ps: JsonObject?, key: String): List<Pair<String, Double>> =
        ps.numMap(key).filterKeys { it != "total" }.entries.sortedByDescending { it.value }.take(16).map { it.key to it.value }

    private fun cards(c: PvCtx): List<Card> {
        val m = c.member; val r = m.raw; val ps = m.playerStats
        fun n(vararg p: String) = ps.num(*p)
        fun row(k: String, v: Double?, big: Boolean = false) = v?.takeIf { it != 0.0 }?.let { Row(k, if (big) fmt(it) else full(it), if (big) listOf("§f$k", "§7${full(it)}") else emptyList()) }
        fun card(t: String, vararg rows: Row?) = Card(t, rows.filterNotNull())
        val out = ArrayList<Card>()
        out += card("Economy", row("Purse", m.purse, true), row("Bank", c.profile.bank, true),
            row("Personal Bank", r.num("profile", "bank_account"), true), row("Motes", r.num("currencies", "motes_purse"), true),
            row("Coins Spent (Bazaar)", n("bazaar", "coins_spent"), true))
        out += card("Gifts", row("Given", n("gifts", "total_given")), row("Received", n("gifts", "total_received")))
        out += card("Season of Jerry", row("Most Snowballs Hit", n("winter", "most_snowballs_hit")), row("Most Damage Dealt", n("winter", "most_damage_dealt")),
            row("Most Magma Damage Dealt", n("winter", "most_magma_damage_dealt")), row("Most Cannonballs Hit", n("winter", "most_cannonballs_hit")))
        val df = ps.obj("end_island", "dragon_fight")
        out += card("Dragons", row("Most Damage", df.num("most_damage", "best"), true),
            df.num("fastest_kill", "best")?.let { Row("Fastest Kill", "%.1fs".format(it / 1000), listOf("§fFastest Kill") + df.numMap("fastest_kill").filterKeys { it != "best" }.map { (k, v) -> "§7${PvData.pretty(k)}: §f${"%.1fs".format(v / 1000)}" }) },
            row("Summoned", df.numMap("amount_summoned").values.sum()), row("Ender Crystals", df.num("ender_crystals_destroyed")),
            row("Last Hits", ps.num("kills", "ender_dragon")), row("Deaths", ps.num("deaths", "ender_dragon")))
        out += card("Endstone Protector", row("Kills", ps.num("kills", "corrupted_protector")), row("Deaths", ps.num("deaths", "corrupted_protector")))
        out += card("Damage", row("Highest Critical Damage", n("highest_critical_damage"), true), row("Highest Damage", n("highest_damage"), true))
        out += card("Pet Milestones", row("Sea Creatures Killed", n("pets", "milestone", "sea_creatures_killed")), row("Ores Mined", n("pets", "milestone", "ores_mined")))
        out += card("Mythological Event", row("Kills", n("mythos", "kills")), row("Dug Arrows", n("mythos", "burrows_dug_next", "total")),
            row("Dug Monsters", n("mythos", "burrows_dug_combat", "total")), row("Dug Treasure", n("mythos", "burrows_dug_treasure", "total")),
            row("Chains Completed", n("mythos", "burrows_chains_complete", "total")))
        out += upgrades(c.profile.raw)
        val a = ps.obj("auctions")
        out += card("Auctions Sold", row("Fees", a.num("fees"), true), row("Coins Earned", a.num("gold_earned"), true),
            row("Items Sold", a.numMap("total_sold").values.sum()), row("No Bids", a.num("no_bids")))
        out += card("Auctions Bought", row("Bids", a.num("bids")), row("Highest Bid", a.num("highest_bid"), true), row("Won", a.num("won")),
            row("Coins Spent", a.num("gold_spent"), true), row("Items Bought", a.numMap("total_bought").values.sum()))
        val claimed = (r.obj("player_data", "claimed_items") ?: c.profile.raw.obj("claimed_items"))?.entrySet()?.mapNotNull { (k, v) ->
            runCatching { Row(PvData.pretty(k), ago(v.asLong)) }.getOrNull()
        }.orEmpty()
        out += Card("Claimed Items", claimed)
        out += card("Uncategorized", row("Soulflow", r.num("item_data", "soulflow"), true),
            row("Items Fished", n("items_fished", "total")), row("Glowing Mushrooms Broken", n("glowing_mushrooms_broken")))
        return out.filter { it.rows.isNotEmpty() }
    }

    private val UPGRADES = listOf("island_size" to 10, "minion_slots" to 5, "guests_count" to 5, "coop_slots" to 3, "coins_allowance" to 5)
    private fun upgrades(p: JsonObject): Card {
        val tiers = HashMap<String, Int>()
        p.arr("community_upgrades", "upgrade_states")?.forEach { e ->
            runCatching { val o = e.asJsonObject; val k = o.get("upgrade").asString; tiers[k] = max(tiers[k] ?: 0, o.get("tier").asInt) }
        }
        if (tiers.isEmpty()) return Card("Upgrades", emptyList())
        return Card("Upgrades", UPGRADES.map { (k, mx) -> val t = tiers[k] ?: 0; Row(PvData.pretty(k), (if (t >= mx) "§6" else "§f") + "$t / $mx") })
    }

    private fun cols(w: Int) = max(1, (w + GAP) / (170 + GAP))

    // Masonry placement, cached per member + width. Offsets are relative to the cards' origin.
    private fun layout(c: PvCtx, area: PvRect): List<Triple<Card, Int, Int>> {
        if (layW == area.w && layKey === c.member) return lay
        val cols = cols(area.w)
        val cw = (area.w - GAP * (cols - 1)) / cols
        val hs = IntArray(cols)
        val out = ArrayList<Triple<Card, Int, Int>>()
        for (cd in built(c).cards) {
            var i = 0
            for (k in 1 until cols) if (hs[k] < hs[i]) i = k
            out += Triple(cd, i * (cw + GAP), hs[i]); hs[i] += cd.h + GAP
        }
        lay = out; layH = max(0, (hs.maxOrNull() ?: 0) - GAP); layW = area.w; layKey = c.member
        return out
    }

    private fun perRow(w: Int) = max(1, (w - 18 + TGAP) / (TILE_MIN_W + TGAP))
    private fun tilesH(c: PvCtx, area: PvRect): Int {
        val per = perRow(area.w); val n = built(c).cons.size
        return 21 + ((n + per - 1) / per) * (TILE_H + TGAP) - TGAP + 9
    }
    private fun killsH(c: PvCtx): Int { val b = built(c); return 21 + 14 + 12 + max(b.kills.size, b.deaths.size) * ROW + 6 }

    override fun height(c: PvCtx, area: PvRect): Int { layout(c, area); return tilesH(c, area) + GAP + killsH(c) + GAP + layH }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme; val b = built(c)
        var y = area.y
        // Consumables: wrapping grid, tiles stretch to fill each row.
        val th = tilesH(c, area)
        val cy0 = c.card(area.x, y, area.w, th, "Consumables")
        val per = perRow(area.w)
        val tw = (area.w - 18 - TGAP * (per - 1)) / per
        for ((i, cs) in b.cons.withIndex()) {
            val tx = area.x + 9 + (i % per) * (tw + TGAP)
            val ty = cy0 + (i / per) * (TILE_H + TGAP)
            val col = if (cs.maxed) t.gold else t.fg
            c.panel(tx, ty, tw, TILE_H, 5, t.panel2, if (cs.maxed) t.gold else t.line)
            c.stack(LootIcons.icon(cs.def.id) ?: cs.def.fallback, tx + 6, ty + (TILE_H - 16) / 2)
            val maxW = tw - 34
            if (cs.shownW != maxW) {
                var nm = cs.def.name
                while (nm.length > 3 && c.textW(nm, PvCtx.S_SM) > maxW) nm = nm.dropLast(2) + "…"
                cs.shownName = nm; cs.shownW = maxW
            }
            c.bold(cs.shownName, tx + 28, ty + 6, col, PvCtx.S_SM)
            c.text(cs.amount, tx + 28, ty + 16, if (cs.maxed) t.gold else t.mut, PvCtx.S_SM)
            c.bar(tx + 28, ty + TILE_H - 6, tw - 36, 2, cs.n.toDouble() / cs.def.max, if (cs.maxed) t.gold else t.acc)
            c.tip(tx, ty, tw, TILE_H, cs.tip)
        }
        y += th + GAP
        // Kills / deaths
        val kh = killsH(c)
        var ky = c.card(area.x, y, area.w, kh, "Kills")
        c.legacy(b.totals, area.x + 9, ky, PvCtx.S_MD, t.mut)
        ky += 14
        val half = (area.w - 18 - GAP) / 2
        for (i in 0..1) {
            val cx = area.x + 9 + i * (half + GAP)
            c.bold(if (i == 0) "Kills" else "Deaths", cx, ky, t.fg, PvCtx.S_SM)
            val list = if (i == 0) b.kills else b.deaths
            for ((j, e) in list.withIndex()) {
                val ry = ky + 12 + j * ROW
                c.legacy(e.first, cx, ry, PvCtx.S_SM)
                c.text(e.second, cx + half - c.textW(e.second, PvCtx.S_SM), ry, t.fg, PvCtx.S_SM)
            }
        }
        y += kh + GAP
        // Masonry cards
        val cols = cols(area.w)
        val cw = (area.w - GAP * (cols - 1)) / cols
        for ((cd, ox, oy) in layout(c, area)) {
            val x = area.x + ox
            var ry = c.card(x, y + oy, cw, cd.h, cd.title)
            for (rw in cd.rows) {
                c.text(rw.k, x + 9, ry, t.mut, PvCtx.S_SM)
                val vw = c.textW(rw.v, PvCtx.S_SM); c.legacy(rw.v, x + cw - 9 - vw, ry, PvCtx.S_SM)
                if (rw.tip.isNotEmpty()) c.tip(x + 4, ry - 1, cw - 8, ROW, rw.tip)
                ry += ROW
            }
        }
    }

    private fun ago(ms: Long): String {
        val d = (System.currentTimeMillis() - ms) / 86_400_000L
        return when { d >= 365 -> "${d / 365}y ago"; d >= 30 -> "${d / 30}mo ago"; else -> "${d}d ago" }
    }
}
