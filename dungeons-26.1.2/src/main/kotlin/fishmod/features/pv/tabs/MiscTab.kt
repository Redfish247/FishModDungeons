package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

object MiscTab : PvTab {
    override val id = "misc"
    override val title = "Misc"

    private const val GAP = 10
    private const val ROW = 11
    private const val TILE_W = 118
    private const val TILE_H = 30

    private class Row(val k: String, val v: String, val tip: List<String> = emptyList())
    private class Card(val title: String, val rows: List<Row>) { val h get() = 21 + rows.size * ROW + 5 }
    private class Cons(val name: String, val icon: ItemStack, val n: Int, val max: Int)

    private fun consumables(m: PvMember): List<Cons> {
        val r = m.raw
        return listOf(
            Cons("Teleporter Pill", ItemStack(Items.ENDER_PEARL), if (r.bool("item_data", "teleporter_pill_consumed") == true) 1 else 0, 1),
            Cons("Metaphysical Serum", ItemStack(Items.POTION), r.int("experimentation", "serums_drank") ?: 0, 3),
            Cons("Reaper Peppers", ItemStack(Items.RED_DYE), r.int("player_data", "reaper_peppers_eaten") ?: 0, 5),
            Cons("McGrubber's Burgers", ItemStack(Items.COOKED_BEEF), r.int("rift", "castle", "grubber_stacks") ?: 0, 5),
            Cons("Wiggling Larvae", ItemStack(Items.SLIME_BALL), r.int("garden_player_data", "larva_consumed") ?: 0, 15),
            Cons("Refined Jyrre", ItemStack(Items.EXPERIENCE_BOTTLE), r.int("winter_player_data", "refined_jyrre_uses") ?: 0, 20),
            Cons("Refined Dark Cacao", ItemStack(Items.COCOA_BEANS), r.int("events", "easter", "refined_dark_cacao_truffles") ?: 0, 20),
        )
    }

    private fun kd(ps: JsonObject?, key: String): List<Pair<String, Double>> =
        ps.numMap(key).filterKeys { it != "total" }.entries.sortedByDescending { it.value }.take(16).map { it.key to it.value }

    private fun cards(c: PvCtx): List<Card> {
        val m = c.member; val r = m.raw; val ps = m.playerStats
        fun n(vararg p: String) = ps.num(*p)
        fun row(k: String, v: Double?, big: Boolean = false) = v?.takeIf { it != 0.0 }?.let { Row(k, if (big) fmt(it) else full(it), if (big) listOf("§f${full(it)}") else emptyList()) }
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
            df.num("fastest_kill", "best")?.let { Row("Fastest Kill", "%.1fs".format(it / 1000), df.numMap("fastest_kill").filterKeys { it != "best" }.map { (k, v) -> "§7${PvData.pretty(k)}: §f${"%.1fs".format(v / 1000)}" }) },
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
            row("Items Fished", n("items_fished", "total")), row("Glowing Mushrooms Broken", n("glowing_mushrooms_broken")),
            row("Highest Damage", n("highest_damage"), true))
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

    private fun layout(c: PvCtx, area: PvRect): Pair<List<Triple<Card, Int, Int>>, Int> {
        val cols = max(1, (area.w + GAP) / (170 + GAP))
        val cw = (area.w - GAP * (cols - 1)) / cols
        val hs = IntArray(cols)
        val out = ArrayList<Triple<Card, Int, Int>>()
        for (cd in cards(c)) {
            val i = hs.indices.minBy { hs[it] }
            out += Triple(cd, area.x + i * (cw + GAP), hs[i]); hs[i] += cd.h + GAP
        }
        return out to (hs.maxOrNull() ?: 0)
    }

    private fun tilesH(area: PvRect): Int { val per = max(1, (area.w - 18 + 6) / (TILE_W + 6)); return 21 + ((7 + per - 1) / per) * (TILE_H + 6) + 4 }
    private fun killsH(c: PvCtx): Int = 21 + 14 + max(kd(c.member.playerStats, "kills").size, kd(c.member.playerStats, "deaths").size) * ROW + 16

    override fun height(c: PvCtx, area: PvRect) = tilesH(area) + GAP + killsH(c) + GAP + layout(c, area).second

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme; val m = c.member
        var y = area.y
        // Consumables
        val th = tilesH(area)
        val cy0 = c.card(area.x, y, area.w, th, "Consumables")
        var tx = area.x + 9; var ty = cy0
        for (cs in consumables(m)) {
            if (tx + TILE_W > area.right - 9) { tx = area.x + 9; ty += TILE_H + 6 }
            val maxed = cs.n >= cs.max
            c.panel(tx, ty, TILE_W, TILE_H, 5, t.panel2, if (maxed) t.gold else t.line)
            c.stack(cs.icon, tx + 6, ty + 7)
            c.text(cs.name, tx + 26, ty + 6, t.fg, PvCtx.S_SM)
            c.bold("${cs.n.coerceAtMost(cs.max)} / ${cs.max}", tx + 26, ty + 17, if (maxed) t.gold else t.mut, PvCtx.S_SM)
            tx += TILE_W + 6
        }
        y += th + GAP
        // Kills
        val ps = m.playerStats
        val kh = killsH(c)
        var ky = c.card(area.x, y, area.w, kh, "Kills")
        c.legacy("Total Kills: §f${full(ps.num("kills", "total") ?: 0.0)}    §7Total Deaths: §f${full(ps.num("deaths", "total") ?: 0.0)}", area.x + 9, ky, PvCtx.S_MD, t.mut)
        ky += 14
        val half = (area.w - 18 - GAP) / 2
        for ((i, key) in listOf("kills", "deaths").withIndex()) {
            val cx = area.x + 9 + i * (half + GAP)
            c.bold(PvData.pretty(key), cx, ky, t.fg, PvCtx.S_SM)
            for ((j, e) in kd(ps, key).withIndex()) {
                val ry = ky + 12 + j * ROW
                c.legacy("§8#${j + 1} §7${PvData.pretty(e.first)}", cx, ry, PvCtx.S_SM)
                val v = full(e.second); c.text(v, cx + half - c.textW(v, PvCtx.S_SM), ry, t.fg, PvCtx.S_SM)
            }
        }
        y += kh + GAP
        // Masonry cards
        val cols = max(1, (area.w + GAP) / (170 + GAP))
        val cw = (area.w - GAP * (cols - 1)) / cols
        for ((cd, x, oy) in layout(c, area).first) {
            var ry = c.card(x, y + oy, cw, cd.h, cd.title)
            for (rw in cd.rows) {
                c.text(rw.k, x + 9, ry, t.mut, PvCtx.S_SM)
                val vw = c.textW(rw.v, PvCtx.S_SM); c.legacy(rw.v, x + cw - 9 - vw, ry, PvCtx.S_SM)
                c.tip(x + 4, ry - 1, cw - 8, ROW, if (rw.tip.isEmpty()) emptyList() else listOf("§f${rw.k}") + rw.tip)
                ry += ROW
            }
        }
    }

    private fun ago(ms: Long): String {
        val d = (System.currentTimeMillis() - ms) / 86_400_000L
        return when { d >= 365 -> "${d / 365}y ago"; d >= 30 -> "${d / 30}mo ago"; else -> "${d}d ago" }
    }
}
