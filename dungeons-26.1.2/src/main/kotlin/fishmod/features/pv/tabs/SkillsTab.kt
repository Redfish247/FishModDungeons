package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

object SkillsTab : PvTab {
    override val id = "skills"
    override val title = "Skills"
    override val subTabs = listOf("Mining", "Fishing", "Farming", "Foraging & Hunting", "Enchanting", "Other Skills")

    // Content height measured on the last render (one-frame lag is fine for scrolling).
    private val lastH = HashMap<Int, Int>()
    private var shardFilter = 0 // 0 all, 1 unlocked, 2 missing

    private val ICONS by lazy {
        mapOf(
            "farming" to ItemStack(Items.GOLDEN_HOE), "mining" to ItemStack(Items.STONE_PICKAXE), "combat" to ItemStack(Items.STONE_SWORD),
            "foraging" to ItemStack(Items.JUNGLE_SAPLING), "fishing" to ItemStack(Items.FISHING_ROD), "enchanting" to ItemStack(Items.ENCHANTING_TABLE),
            "alchemy" to ItemStack(Items.BREWING_STAND), "taming" to ItemStack(Items.LEAD), "hunting" to ItemStack(Items.BOW),
            "carpentry" to ItemStack(Items.CRAFTING_TABLE), "runecrafting" to ItemStack(Items.MAGMA_CREAM), "social" to ItemStack(Items.EMERALD),
        )
    }

    override fun height(c: PvCtx, area: PvRect): Int = lastH[c.sub] ?: area.h

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val bottom = when (c.sub) {
            0 -> mining(c, area)
            1 -> fishing(c, area)
            2 -> farming(c, area)
            3 -> foraging(c, area)
            4 -> enchanting(c, area)
            else -> other(c, area)
        }
        lastH[c.sub] = bottom - area.y + 4
    }

    // ---------- shared layout ----------
    private class KV(val label: String, val value: String, val tip: List<String> = emptyList())
    private class Card(val title: String, val rows: List<KV>, val chips: List<KV> = emptyList())

    private const val ROW = 11

    private fun chipRows(c: PvCtx, chips: List<KV>, w: Int): Int {
        if (chips.isEmpty()) return 0
        var x = 0; var rows = 1
        for (ch in chips) { val cw = c.textW(ch.label, PvCtx.S_SM) + 16; if (x + cw > w && x > 0) { rows++; x = 0 }; x += cw }
        return rows
    }

    private fun cardH(c: PvCtx, k: Card, w: Int): Int = 21 + k.rows.size * ROW + chipRows(c, k.chips, w - 18) * 16 + 6

    private fun drawCard(c: PvCtx, x: Int, y: Int, w: Int, k: Card): Int {
        val h = cardH(c, k, w)
        var cy = c.card(x, y, w, h, k.title)
        for (r in k.rows) { kv(c, x + 9, cy, w - 18, r); cy += ROW }
        var cx = x + 9
        for (ch in k.chips) {
            val cw = c.textW(ch.label, PvCtx.S_SM) + 16
            if (cx + cw > x + w - 9 && cx > x + 9) { cx = x + 9; cy += 16 }
            val pw = c.pill(cx, cy, ch.label, false)
            c.tip(cx, cy, pw, 13, ch.tip)
            cx += cw
        }
        return y + h
    }

    private fun kv(c: PvCtx, x: Int, y: Int, w: Int, r: KV) {
        c.text(r.label, x, y, c.theme.mut, PvCtx.S_SM)
        val vw = c.textW(r.value, PvCtx.S_SM)
        c.legacy(r.value, x + w - vw, y, PvCtx.S_SM)
        c.tip(x, y - 1, w, ROW, r.tip)
    }

    private fun column(c: PvCtx, x: Int, y: Int, w: Int, cards: List<Card>): Int {
        var cy = y
        for (k in cards) cy = drawCard(c, x, cy, w, k) + 8
        return cy
    }

    private fun two(c: PvCtx, area: PvRect, y: Int, left: List<Card>, right: List<Card>): Int {
        val w = (area.w - 10) / 2
        return max(column(c, area.x, y, w, left), column(c, area.x + w + 10, y, w, right))
    }

    private fun skillRow(c: PvCtx, x: Int, y: Int, w: Int, key: String, extra: List<String> = emptyList()): Int {
        val s = c.member.skills.firstOrNull { it.key == key } ?: return y
        c.levelRow(x, y, w, ICONS[key], s.name, s.level, extra, !s.apiDisabled)
        return y + 26
    }

    private fun n(v: Number?): String = if (v == null) "?" else full(v.toDouble())
    private fun pretty(s: String) = PvData.pretty(s)

    // ---------- skill trees ----------
    // Node: display name, api ids, max level, kind (0 perk, 1 ability, 2 peak).
    private class Node(val name: String, val ids: List<String>, val max: Int, val kind: Int)

    private fun idOf(name: String): String = name.lowercase().replace("'", "").replace(Regex(" ii$"), "_2").replace(Regex(" i$"), "_1")
        .replace(Regex("[^a-z0-9_ ]"), "").trim().replace(' ', '_')

    private fun node(spec: String?): Node? {
        if (spec == null) return null
        val p = spec.split('|')
        val name = p[0]
        val ids = listOf(idOf(name)) + (p.getOrNull(1)?.split(',')?.filter { it.isNotBlank() } ?: emptyList())
        val kind = when (p.getOrNull(3)) { "a" -> 1; "p" -> 2; else -> 0 }
        val mx = p.getOrNull(2)?.toIntOrNull() ?: if (kind == 1) 1 else 50
        return Node(name, ids, mx, kind)
    }

    // Rows listed top (tier 10) to bottom (tier 1); 7 columns. Matches the approved prototype layout.
    private val HOTM: List<List<Node?>> = listOf(
        listOf(null, "Gemstone Infusion||1|a", null, "Gifts from the Departed||100", null, "Hungry for More|dead_mans_chest|50", null),
        listOf(null, "Metal Head||20", null, "Rags to Riches|rags_of_riches|50", null, "Eager Adventurer||100", null),
        listOf("Miner's Blessing||30", null, "No Stone Unturned||50", null, "Strong Arm||100", null, "Steady Hand||100"),
        listOf("Anomalous Desire||1|a", null, "Blockhead||20", null, "Gemstone Fortune|gem_lover|100", null, "Maniac Miner||1|a"),
        listOf(null, "Professional||140", null, "Mole||200", null, "Fortunate|mining_fortune_2|20", null),
        listOf("Front Loaded||1", null, "Great Explorer||20", "Peak of the Mountain|special_0|10|p", "Daily Grind||1", null, "Lonesome Miner||45"),
        listOf(null, "Seasoned Mineman|mining_experience|100", null, "Efficient Miner||100", null, "Orbiter|experience_orbs|80", null),
        listOf("Pickobulus|pickaxe_toss|1|a", null, "Titanium Insanium||50", "Mining Fortune||50", "Quick Forge|forge_time|20", null, "Mining Speed Boost||1|a"),
        listOf(null, "Mining Madness||1", null, "Mining Speed II||50", null, "Daily Powder||1", null),
        listOf(null, null, null, "Mining Speed||50", null, null, null),
    ).map { r -> r.map { node(it) } }

    private val HOTF: List<List<Node?>> = listOf(
        listOf(null, "Galatea's Gift||1|a", null, "Mangrove Mastery||50", null, "Fig Fanatic||50", null),
        listOf("Forest Strength||50", null, "Sweep II||50", null, "Hunter's Luck||50", null, "Tree Whisperer||50"),
        listOf(null, "Lottery||20", null, "Foraging Wisdom||50", null, "Daily Wishes||1", null),
        listOf("Woodsplitter||1|a", null, "Axe Sharpening||20", null, "Gift of the Forest||20", null, "Moonlit Harvest||20"),
        listOf(null, "Pristine Bark||50", null, "Forest Essence||50", null, "Tree Rings||50", null),
        listOf("Leaf Sweep||1", "Hunting Fortune||50", "Sweep||50", "Center of the Forest|special_0|7|p", "Foraging Fortune II||50", "Shard Luck||50", "Lumberjack||1|a"),
        listOf(null, "Foraging Speed||50", null, "Efficient Chopper||50", null, "Seasoned Forager||50", null),
        listOf("Axed||1|a", null, "Foraging Fortune||50", null, "Forager||50", null, "Treecapitator Boost||20"),
        listOf(null, "Sweep I||50", null, "Strong Arms||50", null, "Whispers Power||50", null),
        listOf(null, null, null, "Foraging Speed I||50", null, null, null),
    ).map { r -> r.map { node(it) } }

    private val HOTM_XP = longArrayOf(0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000)

    private const val CELL = 15
    private const val GAP = 3

    private fun treeCard(c: PvCtx, area: PvRect, y: Int, title: String, tree: List<List<Node?>>, data: PvSkillTree?, tierFromXp: Boolean): Int {
        val t = c.theme
        val gridW = 7 * (CELL + GAP) - GAP
        val gridH = tree.size * (CELL + GAP) - GAP
        val h = 21 + gridH + 14
        val nodes = data?.nodes ?: emptyMap()
        val usedIds = HashSet<String>()
        val unlocked = if (tierFromXp && data?.xp != null) HOTM_XP.count { data.xp >= it }
            else tree.indices.filter { ri -> tree[ri].any { nd -> nd != null && nd.ids.any { (nodes[it] ?: 0) > 0 } } }.maxOfOrNull { tree.size - it } ?: 0
        c.card(area.x, y, area.w, h, null)
        c.bold(title, area.x + 9, y + 7, t.fg, PvCtx.S_LG)
        c.text("Tier $unlocked / ${tree.size}", area.x + 13 + c.textW(title, PvCtx.S_LG), y + 8, t.mut, PvCtx.S_SM)
        val gx = area.x + 12; val gy = y + 23
        var maxed = 0; var spent = 0
        for ((ri, row) in tree.withIndex()) {
            val tier = tree.size - ri
            for ((ci, nd) in row.withIndex()) {
                if (nd == null) continue
                val lv = nd.ids.firstNotNullOfOrNull { nodes[it] } ?: 0
                usedIds += nd.ids
                val mx = max(nd.max, lv)
                val locked = lv == 0 && tier > unlocked
                val isMax = lv > 0 && lv >= mx
                if (isMax) maxed++
                spent += lv
                val x = gx + ci * (CELL + GAP); val cy = gy + ri * (CELL + GAP)
                val fill = when { isMax -> t.gold; lv > 0 -> t.acc; else -> t.track }
                val r = if (nd.kind == 2) CELL / 2f else 3f
                c.ring(x, cy, CELL, CELL, r, fill, if (nd.kind == 1) t.fg else if (locked) t.line else fill)
                val lbl = when { nd.kind == 1 -> "A"; nd.kind == 2 -> "$lv"; lv > 0 && !isMax -> "$lv"; else -> "" }
                if (lbl.isNotEmpty()) {
                    val ink = if (lv > 0) t.accInk else t.mut
                    c.text(lbl, x + (CELL - c.textW(lbl, PvCtx.S_XS)) / 2, cy + 5, ink, PvCtx.S_XS)
                }
                val tip = ArrayList<String>()
                tip += "§f${nd.name} §8· Tier $tier"
                tip += "§7" + when (nd.kind) { 1 -> "Ability"; 2 -> "Core perk"; else -> "Perk" }
                tip += "§7Level: " + (if (isMax) "§6" else "§a") + "$lv §7/ $mx"
                if (locked) tip += "§cLocked: reach tier $tier"
                if (nd.kind == 1 && lv > 0) tip += if (nd.ids.contains(data?.selectedAbility?.lowercase())) "§aSELECTED" else "§8Not selected"
                c.tip(x, cy, CELL, CELL, tip)
            }
        }
        // Side panel
        val sx = gx + gridW + 18; val sw = area.right - sx - 12
        var sy = gy
        val other = nodes.filterKeys { it !in usedIds && !it.startsWith("toggle") }
        val rows = listOfNotNull(
            KV("Perks maxed", "$maxed"),
            KV("Levels spent", "$spent", listOfNotNull(data?.tokensSpent?.let { "§7Tokens spent: §f$it" })),
            KV("Selected ability", data?.selectedAbility?.let { pretty(it) } ?: "None"),
            data?.xp?.let { KV("Tree XP", fmt(it.toDouble()), listOf("§7${full(it.toDouble())} XP")) },
            if (other.isNotEmpty()) KV("Other perks", "${other.size}", listOf("§fNodes not on this layout") + other.map { (k, v) -> "§7${pretty(k)}: §f$v" }) else null,
        )
        for (r in rows) { kv(c, sx, sy, sw, r); sy += ROW + 2 }
        sy += 6
        for ((col, lbl) in listOf(t.gold to "Maxed", t.acc to "Leveled", t.track to "Locked")) {
            c.ring(sx, sy, 8, 8, 2f, col, col)
            c.text(lbl, sx + 12, sy + 1, t.mut, PvCtx.S_XS)
            sy += 11
        }
        if (data == null) c.text("No tree data", sx, sy + 4, t.bad, PvCtx.S_SM)
        return y + h + 8
    }

    // ---------- Mining ----------
    private val CRYSTALS = listOf("jade", "amber", "amethyst", "sapphire", "topaz")

    private fun mining(c: PvCtx, area: PvRect): Int {
        val m = c.member; val raw = m.raw
        var y = skillRow(c, area.x, area.y, area.w, "mining")
        y = treeCard(c, area, y, "Heart of the Mountain", HOTM, m.hotm, true)

        val core = raw.obj("mining_core")
        val cry = core.obj("crystals")
        val placed = CRYSTALS.map { it to (cry.int(it, "total_placed") ?: 0) }
        val nucleus = placed.minOfOrNull { it.second }?.takeIf { cry != null } ?: raw.int("leveling", "completions", "NUCLEUS_RUNS")
        val hollows = Card("Crystal Hollows", listOf(
            KV("Nucleus runs", n(nucleus), listOf("§7Lowest crystal placement count")),
            KV("Crystals placed", n(placed.sumOf { it.second }), listOf("§fCrystals placed") + placed.map { (k, v) -> "§7${pretty(k)}: §f$v" }),
        ))
        val pw = m.hotm?.powder ?: emptyMap()
        val powder = Card("Powder", listOf("mithril" to "§2", "gemstone" to "§d", "glacite" to "§b").map { (k, col) ->
            val p = pw[k]
            KV(pretty(k), if (p == null) "?" else "$col${fmt(p.total.toDouble())}", if (p == null) listOf("§7No data") else listOf(
                "$col${pretty(k)} Powder", "§7Available: §f${full(p.available.toDouble())}", "§7Spent: §f${full(p.spent.toDouble())}", "§7Total: §f${full(p.total.toDouble())}"))
        })

        val gl = raw.obj("glacite_player_data")
        val corpses = gl.obj("corpses_looted")
        val ctypes = listOf("lapis" to "§9", "umber" to "§6", "tungsten" to "§7", "vanguard" to "§b")
        val corpseRows = ctypes.map { (k, col) -> KV("$col${pretty(k)} corpses", n(corpses.int(k) ?: 0)) }
        val fossilsOwned = gl.arr("fossils_donated")?.mapNotNull { runCatching { it.asString.lowercase() }.getOrNull() }?.toSet() ?: emptySet()
        val fossils = listOf("tusk", "ugly", "webbed", "footprint", "helix", "claw", "clubbed", "spine").map { f ->
            val has = fossilsOwned.contains(f)
            KV((if (has) "§a" else "§8") + pretty(f), "", listOf("§f${pretty(f)} Fossil", if (has) "§aDonated" else "§7Not donated"))
        }
        val comms = raw.int("objectives", "tutorial_commissions") ?: raw.int("player_stats", "mining", "commissions") ?: gl.int("commissions")
        val glacite = Card("Glacite Tunnels", corpseRows + listOf(
            KV("Total corpses", n(ctypes.sumOf { corpses.int(it.first) ?: 0 })),
            KV("Mineshafts entered", n(gl.int("mineshafts_entered"))),
            KV("Commissions", n(comms), if (comms == null) listOf("§7Not exposed by the API") else emptyList()),
            KV("Fossils", "${fossilsOwned.size} / 8"),
        ), fossils)
        return two(c, area, y, listOf(hollows, powder), listOf(glacite))
    }

    // ---------- Fishing ----------
    private val TROPHY = listOf("blobfish", "flyfish", "golden_fish", "gusher", "karate_fish", "lavahorse", "mana_ray", "moldfin",
        "obfuscated_fish_1", "obfuscated_fish_2", "obfuscated_fish_3", "skeleton_fish", "slugfish", "soul_fish",
        "steaming_hot_flounder", "sulphur_skitter", "vanille", "volcanic_stonefish")
    private val SEA = listOf("sea_walker", "sea_guardian", "sea_witch", "sea_archer", "rider_of_the_deep", "catfish", "carrot_king",
        "sea_leech", "guardian_defender", "deep_sea_protector", "water_hydra", "sea_emperor", "the_loch_emperor", "night_squid",
        "agarimoo", "oasis_rabbit", "oasis_sheep", "water_worm", "poisoned_water_worm", "zombie_miner", "flaming_worm", "lava_blaze",
        "lava_pigman", "magma_slug", "moogma", "lava_leech", "pyroclastic_worm", "lava_flame", "fire_eel", "taurus", "thunder",
        "lord_jawbus", "plhlegblast", "great_white_shark", "tiger_shark", "blue_shark", "nurse_shark", "frozen_steve", "frosty_the_snowman",
        "grinch", "yeti", "reindrake", "nutcracker", "scarecrow", "nightmare", "werewolf", "phantom_fisherman", "grim_reaper",
        "abyssal_miner", "alligator", "banshee", "bayou_sludge", "dumpster_diver", "titanoboa", "fiery_scuttler", "ragnarok",
        "wiki_tiki", "blue_ringed_octopus")

    private fun fishing(c: PvCtx, area: PvRect): Int {
        val m = c.member; val ps = m.playerStats
        var y = skillRow(c, area.x, area.y, area.w, "fishing")
        val fished = ps.obj("items_fished")
        val stats = Card("Fishing", listOf(
            KV("Items fished", n(fished.num("total")), listOfNotNull("§fItems fished",
                fished.num("normal")?.let { "§7Normal: §f${full(it)}" }, fished.num("treasure")?.let { "§7Treasure: §6${full(it)}" },
                fished.num("large_treasure")?.let { "§7Large treasure: §6${full(it)}" })),
            KV("Treasures", n(fished.num("treasure"))),
            KV("Shredder fished", n(ps.num("shredder_rod", "fished")), listOfNotNull(ps.num("shredder_rod", "bait")?.let { "§7Bait used: §f${full(it)}" })),
            KV("Trophy fish caught", n(m.raw.num("trophy_fish", "total_caught"))),
        ))
        val kills = ps.obj("kills")
        val sea = SEA.mapNotNull { k -> kills.num(k)?.takeIf { it > 0 }?.let { k to it } }.sortedByDescending { it.second }
        val seaTotal = ps.num("sea_creature_kills")
        val seaCard = Card("Sea Creatures", listOf(KV("Total kills", n(seaTotal ?: sea.sumOf { it.second }))) +
            sea.take(6).map { (k, v) -> KV(pretty(k), full(v)) } +
            (if (sea.size > 6) listOf(KV("…and ${sea.size - 6} more", "", sea.drop(6).map { (k, v) -> "§7${pretty(k)}: §f${full(v)}" })) else emptyList()))
        val w = (area.w - 10) / 2
        val leftBottom = column(c, area.x, y, w, listOf(stats, seaCard))
        val rightBottom = trophyCard(c, area.x + w + 10, y, w)
        return max(leftBottom, rightBottom)
    }

    private fun trophyCard(c: PvCtx, x: Int, y: Int, w: Int): Int {
        val t = c.theme
        val tf = c.member.raw.obj("trophy_fish")
        val h = 21 + 12 + TROPHY.size * ROW + 6
        var cy = c.card(x, y, w, h, "Trophy Fish")
        val tiers = listOf("bronze" to 0xFFCD7F32.toInt(), "silver" to 0xFFC8C8C8.toInt(), "gold" to t.gold, "diamond" to 0xFF55FFFF.toInt())
        val colW = 26
        val c0 = x + w - 9 - tiers.size * colW
        for ((i, p) in tiers.withIndex()) c.text(p.first.take(1).uppercase(), c0 + i * colW + colW - 8, cy, p.second, PvCtx.S_SM)
        cy += 12
        for (f in TROPHY) {
            val counts = tiers.map { tf.int("${f}_${it.first}") ?: 0 }
            val total = tf.int(f) ?: counts.sum()
            val best = counts.indexOfLast { it > 0 }
            val nameCol = if (best >= 0) tiers[best].second else t.mut
            c.text(pretty(f), x + 9, cy, nameCol, PvCtx.S_SM)
            for ((i, v) in counts.withIndex()) {
                val s = if (v == 0) "-" else fmt(v.toDouble())
                c.text(s, c0 + i * colW + colW - c.textW(s, PvCtx.S_SM), cy, if (v == 0) t.mut else t.fg, PvCtx.S_SM)
            }
            c.tip(x + 9, cy - 1, w - 18, ROW, listOf("§f${pretty(f)}", "§7Total caught: §f$total",
                "§7Highest tier: " + if (best >= 0) "§f${pretty(tiers[best].first)}" else "§8none") +
                tiers.mapIndexed { i, p -> "§7${pretty(p.first)}: §f${counts[i]}" })
            cy += ROW
        }
        return y + h + 8
    }

    // ---------- Farming ----------
    private val GARDEN_XP = longArrayOf(0, 70, 140, 280, 520, 1_120, 2_620, 4_620, 7_620, 10_620, 20_620, 30_620, 40_620, 50_620, 60_620)
    private val CROPS = listOf("WHEAT" to "Wheat", "CARROT_ITEM" to "Carrot", "POTATO_ITEM" to "Potato", "MELON" to "Melon", "PUMPKIN" to "Pumpkin",
        "SUGAR_CANE" to "Sugar Cane", "INK_SACK:3" to "Cocoa Beans", "CACTUS" to "Cactus", "MUSHROOM_COLLECTION" to "Mushroom", "NETHER_STALK" to "Nether Wart")

    private fun farming(c: PvCtx, area: PvRect): Int {
        val m = c.member; val raw = m.raw; val p = c.profile
        val jc = raw.obj("jacobs_contest")
        var y = skillRow(c, area.x, area.y, area.w, "farming", listOfNotNull(jc.int("perks", "farming_level_cap")?.let { "§7Anita level cap: §a+$it" }))
        val g = p.garden
        val gx = g?.xp
        val glvl = if (gx == null) null else GARDEN_XP.count { gx >= it }
        val garden = Card("Garden", if (g == null) listOf(KV("Garden", if (p.gardenStatus == "loading") "…" else "unavailable")) else listOf(
            KV("Garden level", n(glvl), listOf("§7Garden XP: §f${n(gx)}")),
            KV("Plots unlocked", "${g.plots ?: 0} / 24"),
            KV("Visitors served", n(g.visitorsServed), listOfNotNull(g.uniqueVisitors?.let { "§7Unique visitors: §f$it" })),
            KV("Copper", "§c" + n(raw.num("garden_player_data", "copper")), listOfNotNull(raw.num("garden_player_data", "larva_consumed")?.let { "§7Larva consumed: §f${full(it)}" })),
        ))
        val res = g?.raw.obj("resources_collected")
        val pbs = jc.obj("personal_bests")
        val crops = Card("Crop Milestones", CROPS.map { (k, nm) ->
            val v = res.num(k)
            KV(nm, if (v == null) "-" else fmt(v), listOfNotNull("§f$nm", "§7Collected on garden: §f${n(v)}", pbs.num(k)?.let { "§7Contest best: §e${full(it)}" }))
        })
        val medals = jc.obj("unique_brackets")
        val inv = jc.obj("medals_inv")
        val contests = jc.obj("contests")?.entrySet()?.size ?: 0
        val chips = listOf("diamond" to "§b", "platinum" to "§3", "gold" to "§6", "silver" to "§f", "bronze" to "§c").map { (k, col) ->
            val uniq = medals.arr(k)?.size() ?: 0
            KV("$col${pretty(k)} §f$uniq", "", listOfNotNull("$col${pretty(k)} brackets", "§7Unique crops: §f$uniq",
                inv.int(k)?.let { "§7Medals in inventory: §f$it" }, "§7Contests entered: §f$contests"))
        }
        val contestCard = Card("Jacob's Contests", listOf(
            KV("Contests entered", n(contests)),
            KV("Double drops perk", n(jc.int("perks", "double_drops") ?: 0)),
        ), chips)
        return two(c, area, y, listOf(garden, contestCard), listOf(crops))
    }

    // ---------- Foraging & Hunting ----------
    private val SHARDS = listOf(
        "Grove" to "COMMON", "Mist" to "COMMON", "Flash" to "COMMON", "Phanpyre" to "COMMON", "Cod" to "COMMON", "Hideonleaf" to "COMMON",
        "Verdant" to "COMMON", "Chill" to "COMMON", "Birries" to "UNCOMMON", "Mossybit" to "UNCOMMON", "Lapis Zombie" to "UNCOMMON",
        "Sea Archer" to "UNCOMMON", "Kada Knight" to "UNCOMMON", "Bambuleaf" to "UNCOMMON", "Salmon" to "UNCOMMON", "Termite" to "UNCOMMON",
        "Bal" to "RARE", "Lunar Moth" to "RARE", "Glacite Walker" to "RARE", "Cinderbat" to "RARE", "Pest" to "RARE", "Lord Jawbus" to "RARE",
        "Bezal" to "RARE", "Yog" to "RARE", "Ent" to "EPIC", "Galaxy Fish" to "EPIC", "Lapis Creeper" to "EPIC", "Tiamat" to "EPIC",
        "Magma Slug" to "EPIC", "Xyz" to "EPIC", "Wartybug" to "EPIC", "Starborn" to "LEGENDARY", "Vanquisher" to "LEGENDARY",
        "Thunder" to "LEGENDARY", "Daemon" to "LEGENDARY", "Leviathan" to "LEGENDARY", "Wither" to "LEGENDARY", "Prince" to "LEGENDARY",
        "Galaxy Moth" to "LEGENDARY", "Kraken" to "LEGENDARY",
    )

    private fun foraging(c: PvCtx, area: PvRect): Int {
        val m = c.member
        var y = skillRow(c, area.x, area.y, area.w, "foraging", listOf("§8Cap from PvTables (54)"))
        y = treeCard(c, area, y, "Heart of the Forest", HOTF, m.hotf, false)
        y = skillRow(c, area.x, y, area.w, "hunting")
        return shardsCard(c, area, y)
    }

    private fun shardsCard(c: PvCtx, area: PvRect, y: Int): Int {
        val t = c.theme
        val attrs = c.member.attributes
        val norm = attrs.mapKeys { it.key.lowercase().replace(Regex("^shard_"), "") }
        val known = SHARDS.map { (nm, rar) -> Triple(nm, rar, norm[idOf(nm)] ?: 0L) }
        val extra = norm.filterKeys { k -> known.none { idOf(it.first) == k } }.map { (k, v) -> Triple(pretty(k), "", v) }
        val all = known + extra
        val own = all.count { it.third > 0 }
        val list = all.filter { when (shardFilter) { 1 -> it.third > 0; 2 -> it.third == 0L; else -> true } }
        val chips = list.map { (nm, rar, cnt) ->
            val code = PvTables.RARITY_CODE[rar] ?: "§7"
            KV((if (cnt > 0) code else "§8") + nm + if (cnt > 0) " §f$cnt" else "", "", listOfNotNull(
                "$code$nm Shard", if (rar.isNotEmpty()) "$code$rar" else null,
                if (cnt > 0) "§7Owned / absorbed: §f$cnt" else "§7Not caught yet",
                if (cnt > 0) "§7Attribute level: §f$cnt" else null))
        }
        val w = area.w
        val ch = chipRows(c, chips, w - 18) * 16
        val h = 21 + 18 + max(ch, 12) + 6
        c.card(area.x, y, w, h, null)
        c.bold("Attribute Shards", area.x + 9, y + 7, t.fg, PvCtx.S_LG)
        c.text("$own / ${all.size} unlocked", area.x + 15 + c.textW("Attribute Shards", PvCtx.S_LG), y + 8, t.mut, PvCtx.S_SM)
        var px = area.x + 9
        for ((i, lbl) in listOf("All ${all.size}", "Unlocked $own", "Missing ${all.size - own}").withIndex())
            px += c.pill(px, y + 21, lbl, shardFilter == i) { shardFilter = i } + 4
        var cx = area.x + 9; var cy = y + 39
        if (chips.isEmpty()) c.text("Nothing here.", cx, cy, t.mut, PvCtx.S_SM)
        for (k in chips) {
            val cw = c.textW(k.label, PvCtx.S_SM) + 16
            if (cx + cw > area.x + w - 9 && cx > area.x + 9) { cx = area.x + 9; cy += 16 }
            val pw = c.pill(cx, cy, k.label, false)
            c.tip(cx, cy, pw, 13, k.tip)
            cx += cw
        }
        return y + h + 8
    }

    // ---------- Enchanting ----------
    private fun enchanting(c: PvCtx, area: PvRect): Int {
        val ex = c.member.raw.obj("experimentation")
        var y = skillRow(c, area.x, area.y, area.w, "enchanting")
        val games = listOf("pairings" to "Superpairs", "simon" to "Chronomatron", "numbers" to "Ultrasequencer")
        val rows = games.map { (k, nm) -> gameRow(ex.obj(k), nm) }
        val card = Card("Experimentation Table", rows + listOf(
            KV("Total claims", n(games.sumOf { g -> sumPrefix(ex.obj(g.first), "claims_") })),
            KV("Claim resets", n(ex.int("claims_resets") ?: 0)),
            KV("Serums drank", n(ex.int("serums_drank") ?: 0)),
        ))
        return two(c, area, y, listOf(card), emptyList())
    }

    private fun sumPrefix(o: JsonObject?, p: String): Int =
        o?.entrySet()?.filter { it.key.startsWith(p) }?.sumOf { runCatching { it.value.asInt }.getOrDefault(0) } ?: 0

    private fun gameRow(o: JsonObject?, nm: String): KV {
        if (o == null) return KV(nm, "§8none")
        val best = o.entrySet().filter { it.key.startsWith("best_score_") }.mapNotNull { e -> e.key.removePrefix("best_score_").toIntOrNull()?.let { it to e.value.asInt } }.sortedBy { it.first }
        val tip = listOf("§f$nm", "§7Attempts: §f${sumPrefix(o, "attempts_")}", "§7Claims: §f${sumPrefix(o, "claims_")}", "") +
            best.map { (tier, s) -> "§7Tier $tier best: §f$s" }
        return KV(nm, "${sumPrefix(o, "claims_")} claims", tip)
    }

    // ---------- Other ----------
    private fun other(c: PvCtx, area: PvRect): Int {
        val m = c.member
        val w = (area.w - 10) / 2
        var y = area.y
        val keys = listOf("alchemy", "carpentry", "taming", "runecrafting", "social")
        for ((i, k) in keys.withIndex()) {
            skillRow(c, area.x + (i % 2) * (w + 10), y, w, k)
            if (i % 2 == 1 || i == keys.lastIndex) y += 26
        }
        y += 4
        val pets = m.pets
        val petCard = Card("Pets", listOf(
            KV("Pets owned", n(pets.size)),
            KV("Unique pets", n(pets.map { it.type }.distinct().size)),
            KV("Max level", n(pets.count { it.level.maxed })),
            KV("Pet candy used", n(pets.sumOf { it.candyUsed })),
            KV("Active", m.activePet?.let { it.rarityCode + it.name } ?: "None", m.activePet?.let { PvPets.tooltip(it) } ?: emptyList()),
        ))
        val tiers = m.minions.values.sumOf { it.size }
        val craft = Card("Crafting", listOf(
            KV("Minion tiers crafted", n(tiers), listOf("§7Unique minion types: §f${m.minions.size}")),
            KV("Essence types", n(m.essence.size), m.essence.map { (k, v) -> "§7${pretty(k)}: §d${full(v.toDouble())}" }),
        ))
        return two(c, area, y, listOf(petCard), listOf(craft))
    }
}
