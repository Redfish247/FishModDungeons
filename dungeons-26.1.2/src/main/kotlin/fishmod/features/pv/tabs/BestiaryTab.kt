package fishmod.features.pv.tabs

import fishmod.features.pv.*
import fishmod.utils.rendering.UiRecorder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BestiaryTab : PvTab {
    override val id = "bestiary"
    override val title = "Bestiary"

    private const val HEAD = 22
    private const val TW = 112
    private const val TH = 30
    private val open = HashSet<String>()

    // Cumulative kills per tier, by bracket.
    private val BRACKETS = arrayOf(
        longArrayOf(20, 40, 60, 100, 200, 400, 800, 1400, 2000, 3000, 6000, 12000, 20000, 30000, 40000, 50000, 60000, 72000, 86000, 100000, 200000, 400000, 600000, 800000, 1000000),
        longArrayOf(5, 10, 15, 25, 50, 100, 200, 350, 500, 750, 1500, 3000, 5000, 7500, 10000, 12500, 15000, 18000, 21500, 25000, 50000, 100000, 150000, 200000, 250000),
        longArrayOf(4, 8, 12, 16, 20, 40, 80, 140, 200, 300, 600, 1200, 2000, 3000, 4000, 5000, 6000, 7200, 8600, 10000, 20000, 40000, 60000, 80000, 100000),
        longArrayOf(2, 4, 6, 10, 15, 20, 25, 35, 50, 75, 150, 300, 500, 750, 1000, 1250, 1500, 1800, 2150, 2500, 5000, 10000, 15000, 20000, 25000),
        longArrayOf(1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 60, 120, 200, 300, 400, 500, 600, 720, 860, 1000, 2000, 4000, 6000, 8000, 10000),
        longArrayOf(1, 2, 3, 5, 7, 9, 14, 17, 21, 25, 50, 80, 125, 175, 250, 325, 425, 525, 625, 750, 1500, 3000, 4500, 6000, 7500),
        longArrayOf(1, 2, 3, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28),
    )

    private class Fam(val name: String, val bracket: Int, val cap: Int, val ids: List<String>)
    private class Area(val name: String, val icon: Item, val fams: List<Fam>)
    private fun f(name: String, b: Int, cap: Int, vararg ids: String) = Fam(name, b, cap, ids.toList())

    private val AREAS by lazy {
        listOf(
            Area("Private Island", Items.GRASS_BLOCK, listOf(f("Cave Spider", 1, 5, "cave_spider"), f("Creeper", 1, 5, "creeper"),
                f("Enderman", 1, 5, "enderman_private"), f("Skeleton", 1, 5, "skeleton"), f("Slime", 1, 5, "slime"),
                f("Spider", 1, 5, "spider"), f("Witch", 1, 5, "witch"), f("Zombie", 1, 5, "zombie", "zombie_villager"))),
            Area("Hub", Items.OAK_SAPLING, listOf(f("Crypt Ghoul", 1, 15, "unburried_zombie"), f("Golden Ghoul", 3, 15, "golden_ghoul"),
                f("Old Wolf", 3, 15, "old_wolf"), f("Wolf", 1, 15, "ruin_wolf"), f("Zombie Villager", 1, 15, "zombie_villager_hub"))),
            Area("The Farming Islands", Items.WHEAT, listOf(f("Chicken", 1, 5, "farming_chicken", "chicken"), f("Cow", 1, 5, "farming_cow", "cow"),
                f("Mushroom Cow", 1, 5, "mushroom_cow"), f("Pig", 1, 5, "farming_pig", "pig"), f("Rabbit", 1, 5, "farming_rabbit", "rabbit"),
                f("Sheep", 1, 5, "farming_sheep", "sheep"))),
            Area("Spider's Den", Items.COBWEB, listOf(f("Arachne", 6, 20, "arachne"), f("Brood Mother", 6, 20, "brood_mother_spider"),
                f("Dasher Spider", 1, 20, "dasher_spider"), f("Gravel Skeleton", 1, 20, "respawning_skeleton"),
                f("Rain Slime", 4, 20, "random_slime"), f("Spider Jockey", 1, 20, "spider_jockey", "jockey_shot_silverfish"),
                f("Splitter Spider", 1, 20, "splitter_spider", "splitter_spider_silverfish"), f("Voracious Spider", 1, 20, "voracious_spider"),
                f("Weaver Spider", 1, 20, "weaver_spider"), f("Arachne's Keeper", 3, 20, "arachne_keeper"), f("Arachne's Brood", 1, 20, "arachne_brood"))),
            Area("The End", Items.END_STONE, listOf(f("Enderman", 1, 25, "enderman"), f("Endermite", 2, 20, "endermite", "nest_endermite"),
                f("Ender Dragon", 6, 20, "unstable_dragon", "strong_dragon", "superior_dragon", "wise_dragon", "young_dragon", "old_dragon", "protector_dragon", "holy_dragon"),
                f("Obsidian Defender", 2, 20, "obsidian_wither"), f("Voidling Extremist", 3, 20, "voidling_extremist"),
                f("Watcher", 2, 20, "watcher"), f("Zealot", 1, 25, "zealot_enderman", "zealot_bruiser"), f("Endstone Protector", 6, 20, "corrupted_protector"))),
            Area("Crimson Isle", Items.NETHERRACK, listOf(f("Ashfang", 6, 20, "ashfang"), f("Barbarian Duke X", 6, 20, "barbarian_duke_x"),
                f("Blaze", 2, 20, "blaze", "bezal", "mutated_blaze"), f("Bladesoul", 6, 20, "bladesoul"), f("Flaming Spider", 1, 20, "flaming_spider"),
                f("Ghast", 2, 20, "ghast", "dive_ghast"), f("Mage Outlaw", 6, 20, "mage_outlaw"), f("Magma Cube", 1, 20, "magma_cube", "magma_cube_rider", "pack_magma_cube"),
                f("Matcho", 5, 20, "matcho"), f("Millennia-Aged Blaze", 3, 20, "old_blaze"), f("Smoldering Blaze", 3, 20, "smoldering_blaze"),
                f("Wither Skeleton", 2, 20, "wither_skeleton"), f("Wither Spectre", 2, 20, "wither_spectre"), f("Kada Knight", 3, 20, "kada_knight"))),
            Area("Crystal Hollows", Items.AMETHYST_SHARD, listOf(f("Automaton", 1, 15, "automaton"), f("Bal", 6, 15, "bal_boss"),
                f("Butterfly", 3, 15, "butterfly"), f("Goblin", 1, 15, "goblin", "goblin_weakling_melee", "goblin_weakling_bow", "goblin_creepertamer", "goblin_battler", "goblin_knife_thrower", "goblin_flamethrower", "goblin_murderlover"),
                f("Key Guardian", 6, 15, "key_guardian"), f("Sludge", 1, 15, "sludge"), f("Team Treasurite", 2, 15, "team_treasurite_grunt", "team_treasurite_viper", "team_treasurite_wendy", "team_treasurite_sebastian", "team_treasurite_corleone"),
                f("Thyst", 1, 15, "thyst"), f("Worm", 2, 15, "worm", "scatha"), f("Yog", 2, 15, "yog"))),
            Area("Dwarven Mines", Items.IRON_PICKAXE, listOf(f("Ghost", 1, 25, "caverns_ghost"), f("Goblin", 1, 15, "goblin_dwarven"),
                f("Ice Walker", 1, 15, "ice_walker"), f("Powder Ghast", 6, 15, "powder_ghast"), f("Star Sentry", 3, 15, "crystal_sentry"),
                f("Treasure Hoarder", 3, 15, "treasure_hoarder"))),
            Area("Catacombs", Items.WITHER_SKELETON_SKULL, listOf(f("Angry Archaeologist", 4, 25, "diamond_guy", "master_diamond_guy"),
                f("Bat", 4, 15, "dungeon_secret_bat"), f("Cellar Spider", 4, 15, "cellar_spider", "master_cellar_spider"),
                f("Crypt Dreadlord", 2, 25, "crypt_dreadlord", "master_crypt_dreadlord"), f("Crypt Lurker", 2, 25, "crypt_lurker", "master_crypt_lurker"),
                f("Crypt Souleater", 2, 25, "crypt_souleater", "master_crypt_souleater"), f("Fels", 2, 25, "tentaclees", "master_tentaclees"),
                f("Golem", 4, 25, "sadan_golem", "master_sadan_golem"), f("King Midas", 6, 15, "king_midas", "master_king_midas"),
                f("Lonely Spider", 2, 25, "lonely_spider", "master_lonely_spider"), f("Lost Adventurer", 4, 25, "lost_adventurer", "master_lost_adventurer"),
                f("Mimic", 6, 15, "mimic", "master_mimic"), f("Scared Skeleton", 2, 25, "scared_skeleton", "master_scared_skeleton"),
                f("Shadow Assassin", 4, 25, "shadow_assassin", "master_shadow_assassin"), f("Skeleton Grunt", 2, 25, "skeleton_grunt", "master_skeleton_grunt"),
                f("Skeleton Lord", 4, 25, "skeleton_lord", "master_skeleton_lord"), f("Skeleton Master", 2, 25, "skeleton_master", "master_skeleton_master"),
                f("Skeleton Soldier", 2, 25, "skeleton_soldier", "master_skeleton_soldier"), f("Skeletor", 2, 25, "skeletor", "skeletor_prime", "master_skeletor", "master_skeletor_prime"),
                f("Sniper", 2, 25, "sniper_skeleton", "master_sniper_skeleton"), f("Super Archer", 2, 25, "super_archer", "master_super_archer"),
                f("Super Tank Zombie", 2, 25, "super_tank_zombie", "master_super_tank_zombie"), f("Tank Zombie", 2, 25, "crypt_tank_zombie", "master_crypt_tank_zombie"),
                f("Terracotta", 2, 25, "sadan_statue", "master_sadan_statue"), f("Undead Skeleton", 2, 25, "watcher_skeleton", "master_watcher_skeleton"),
                f("Undead", 2, 25, "watcher_summon_undead", "master_watcher_summon_undead"), f("Withermancer", 2, 25, "crypt_witherskeleton", "master_crypt_witherskeleton"),
                f("Wither Guard", 4, 25, "wither_guard", "master_wither_guard"), f("Wither Husk", 2, 25, "wither_husk", "master_wither_husk"),
                f("Wither Miner", 2, 25, "wither_miner", "master_wither_miner"), f("Zombie Commander", 2, 25, "zombie_commander", "master_zombie_commander"),
                f("Zombie Grunt", 2, 25, "zombie_grunt", "master_zombie_grunt"), f("Zombie Knight", 2, 25, "zombie_knight", "master_zombie_knight"),
                f("Zombie Lord", 4, 25, "zombie_lord", "master_zombie_lord"), f("Zombie Soldier", 2, 25, "zombie_soldier", "master_zombie_soldier"))),
            Area("Backwater Bayou", Items.LILY_PAD, listOf(f("Alligator", 5, 15, "alligator"), f("Banshee", 5, 15, "banshee"),
                f("Bayou Sludge", 4, 15, "bayou_sludge"), f("Dumpster Diver", 5, 15, "dumpster_diver"), f("Titanoboa", 6, 15, "titanoboa"),
                f("Trash Gobbler", 4, 15, "trash_gobbler"), f("Frog Man", 4, 15, "frog_man"), f("Snapping Turtle", 4, 15, "snapping_turtle"),
                f("Blue Ringed Octopus", 5, 15, "blue_ringed_octopus"), f("Wiki Tiki", 6, 15, "wiki_tiki"))),
            Area("Fishing", Items.FISHING_ROD, listOf(f("Agarimoo", 4, 15, "agarimoo"), f("Carrot King", 4, 15, "carrot_king"),
                f("Catfish", 3, 15, "catfish"), f("Deep Sea Protector", 3, 15, "deep_sea_protector"), f("Guardian Defender", 3, 15, "guardian_defender"),
                f("Night Squid", 3, 15, "night_squid"), f("Oasis Rabbit", 4, 15, "oasis_rabbit"), f("Oasis Sheep", 4, 15, "oasis_sheep"),
                f("Poisoned Water Worm", 3, 15, "poisoned_water_worm"), f("Rider of the Deep", 3, 15, "zombie_miner"), f("Sea Archer", 2, 15, "sea_archer"),
                f("Sea Emperor", 6, 15, "skeleton_emperor", "guardian_emperor"), f("Sea Guardian", 2, 15, "sea_guardian"), f("Sea Leech", 3, 15, "sea_leech"),
                f("Sea Walker", 2, 15, "sea_walker"), f("Sea Witch", 2, 15, "sea_witch"), f("Squid", 1, 15, "pond_squid"),
                f("The Loch Emperor", 6, 15, "the_loch_emperor"), f("Water Hydra", 5, 15, "water_hydra"), f("Water Worm", 3, 15, "water_worm"),
                f("Frozen Steve", 2, 15, "frozen_steve"), f("Frosty", 2, 15, "frosty_the_snowman"), f("Grinch", 5, 15, "grinch"),
                f("Nutcracker", 5, 15, "nutcracker"), f("Reindrake", 6, 15, "reindrake"), f("Yeti", 5, 15, "yeti"))),
            Area("Fishing Festival", Items.PRISMARINE_SHARD, listOf(f("Blue Shark", 3, 15, "blue_shark"), f("Great White Shark", 5, 15, "great_white_shark"),
                f("Nurse Shark", 2, 15, "nurse_shark"), f("Tiger Shark", 4, 15, "tiger_shark"))),
            Area("Lava", Items.LAVA_BUCKET, listOf(f("Fiery Scuttler", 5, 15, "fiery_scuttler"), f("Flaming Worm", 2, 15, "flaming_worm"),
                f("Fried Chicken", 4, 15, "fried_chicken"), f("Lava Blaze", 3, 15, "lava_blaze"), f("Lava Flame", 4, 15, "lava_flame"),
                f("Lava Leech", 3, 15, "lava_leech"), f("Lava Pigman", 3, 15, "lava_pigman"), f("Lord Jawbus", 6, 15, "lord_jawbus"),
                f("Magma Slug", 2, 15, "magma_slug"), f("Moogma", 2, 15, "moogma"), f("Plhlegblast", 6, 15, "pond_squid_plhlegblast", "plhlegblast"),
                f("Pyroclastic Worm", 3, 15, "pyroclastic_worm"), f("Ragnarok", 6, 15, "ragnarok"), f("Taurus", 5, 15, "pig_rider"),
                f("Thunder", 6, 15, "thunder"))),
            Area("Spooky Festival", Items.JACK_O_LANTERN, listOf(f("Crazy Witch", 3, 15, "batty_witch"), f("Headless Horseman", 6, 15, "horseman_horse"),
                f("Phantom Spirit", 3, 15, "phantom_spirit"), f("Scary Jerry", 3, 15, "scary_jerry"), f("Trick or Treater", 3, 15, "trick_or_treater"),
                f("Wither Gourd", 3, 15, "wither_gourd"), f("Wraith", 3, 15, "wraith"), f("Phantom Fisher", 5, 15, "phantom_fisherman"),
                f("Grim Reaper", 6, 15, "grim_reaper"), f("Scarecrow", 3, 15, "scarecrow"), f("Nightmare", 4, 15, "nightmare"),
                f("Werewolf", 4, 15, "werewolf"))),
        )
    }

    private class Tile(val name: String, val kills: Long, val deaths: Long, val tier: Int, val cap: Int, val next: Long?, val bracket: Int)
    private class Sec(val name: String, val icon: ItemStack, val tiles: List<Tile>) { val maxed get() = tiles.count { it.tier >= it.cap } }

    private val ID_TO_FAM by lazy { HashMap<String, Fam>().also { m -> AREAS.forEach { a -> a.fams.forEach { fm -> fm.ids.forEach { m.putIfAbsent(it, fm) } } } } }
    private val FAM_AREA by lazy { HashMap<Fam, Area>().also { m -> AREAS.forEach { a -> a.fams.forEach { m[it] = a } } } }
    private val OTHER_ICON by lazy { ItemStack(Items.BOOK) }
    private val BOOK by lazy { ItemStack(Items.WRITABLE_BOOK) }

    private var cacheKey: Any? = null
    private var cache: List<Sec> = emptyList()

    private fun base(k: String) = k.replace(Regex("_\\d+$"), "")

    private fun tierOf(kills: Long, bracket: Int, cap: Int): Pair<Int, Long?> {
        val b = BRACKETS[(bracket - 1).coerceIn(0, BRACKETS.size - 1)]
        val lim = minOf(cap, b.size)
        val t = (0 until lim).count { kills >= b[it] }
        return t to (if (t < lim) b[t] else null)
    }

    private fun sections(c: PvCtx): List<Sec> {
        val m = c.member
        if (cacheKey === m) return cache
        val kills = HashMap<Fam, Long>(); val deaths = HashMap<Fam, Long>()
        val otherK = HashMap<String, Long>(); val otherD = HashMap<String, Long>()
        for ((k, v) in m.bestiaryKills) { val b = base(k); val fm = ID_TO_FAM[b]; if (fm != null) kills.merge(fm, v, Long::plus) else otherK.merge(b, v, Long::plus) }
        for ((k, v) in m.bestiaryDeaths) { val b = base(k); val fm = ID_TO_FAM[b]; if (fm != null) deaths.merge(fm, v, Long::plus) else otherD.merge(b, v, Long::plus) }
        val out = AREAS.map { a ->
            Sec(a.name, ItemStack(a.icon), a.fams.map { fm ->
                val kl = kills[fm] ?: 0L
                val (t, nx) = tierOf(kl, fm.bracket, fm.cap)
                Tile(fm.name, kl, deaths[fm] ?: 0L, t, fm.cap, nx, fm.bracket)
            })
        }.toMutableList()
        if (otherK.isNotEmpty()) out += Sec("Other", OTHER_ICON, otherK.entries.sortedByDescending { it.value }.map { (k, v) ->
            val (t, nx) = tierOf(v, 3, 25)
            Tile(PvData.pretty(k), v, otherD[k] ?: 0L, t, 25, nx, 3)
        })
        cacheKey = m; cache = out
        return out
    }

    private fun perRow(w: Int) = ((w - 18 + 6) / (TW + 6)).coerceAtLeast(1)
    private fun bodyH(s: Sec, w: Int) = ((s.tiles.size + perRow(w) - 1) / perRow(w)) * (TH + 6) + 6

    override fun height(c: PvCtx, area: PvRect): Int =
        24 + 18 + sections(c).sumOf { s -> HEAD + 4 + if (s.name in open) bodyH(s, area.w) else 0 }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        val secs = sections(c)
        var y = area.y

        // Milestone = total family tiers / 10
        val tiers = secs.sumOf { s -> s.tiles.sumOf { it.tier } }
        val maxTiers = secs.sumOf { s -> s.tiles.sumOf { it.cap } }
        val ms = tiers / 10
        val lvl = PvTables.Level(ms, (tiers % 10) / 10.0, (tiers % 10).toLong(), 10, tiers >= maxTiers, maxTiers / 10, tiers.toLong())
        c.levelRow(area.x, y, area.w, BOOK, "Bestiary Milestone", lvl, listOf(
            "§7Family tiers: §f$tiers §8/ $maxTiers", "§7Families maxed: §f${secs.sumOf { it.maxed }} §8/ ${secs.sumOf { it.tiles.size }}"))
        y += 24

        var px = area.x
        px += c.pill(px, y, "Expand all", false) { secs.forEach { open += it.name } } + 4
        c.pill(px, y, "Collapse all", false) { open.clear() }
        y += 18

        for (s in secs) {
            val isOpen = s.name in open
            val h = HEAD + if (isOpen) bodyH(s, area.w) else 0
            c.panel(area.x, y, area.w, h, 6, t.panel, t.line)
            val hov = c.hovered(area.x, y, area.w, HEAD)
            if (hov) c.rect(area.x, y, area.w, HEAD, t.panel2, 6f)
            c.stack(s.icon, area.x + 4, y + 3)
            c.bold(s.name, area.x + 24, y + 7, t.fg, PvCtx.S_MD)
            val mx = "${s.maxed} / ${s.tiles.size} maxed"
            val barW = 120
            val bx = area.right - 20 - barW
            c.text(mx, bx - 8 - c.textW(mx, PvCtx.S_SM), y + 8, if (s.maxed == s.tiles.size) t.gold else t.mut, PvCtx.S_SM)
            c.bar(bx, y + 9, barW, 4, if (s.tiles.isEmpty()) 0.0 else s.maxed.toDouble() / s.tiles.size, t.gold)
            UiRecorder.chevron(area.right - 11f, y + HEAD / 2f, !isOpen, t.mut)
            val name = s.name
            c.hit(area.x, y, area.w, HEAD) { if (!open.remove(name)) open += name }
            if (isOpen) tiles(c, s, area.x, y + HEAD, area.w)
            y += h + 4
        }
    }

    private fun tiles(c: PvCtx, s: Sec, x: Int, y: Int, w: Int) {
        val t = c.theme
        val per = perRow(w)
        for ((i, tl) in s.tiles.withIndex()) {
            val tx = x + 9 + (i % per) * (TW + 6)
            val ty = y + (i / per) * (TH + 6)
            val maxed = tl.tier >= tl.cap
            c.panel(tx, ty, TW, TH, 5, t.panel2, if (maxed) t.gold else 0)
            c.stack(s.icon, tx + 4, ty + (TH - 16) / 2)
            val nm = "${tl.name} ${tl.tier}"
            var shown = nm
            while (c.textW(shown, PvCtx.S_SM) > TW - 28 && shown.length > 4) shown = shown.dropLast(2) + "…"
            c.bold(shown, tx + 24, ty + 6, if (maxed) t.gold else t.fg, PvCtx.S_SM)
            c.legacy("§7Kills: §f${full(tl.kills.toDouble())}", tx + 24, ty + 17, PvCtx.S_XS)
            c.tip(tx, ty, TW, TH, listOfNotNull(
                "§f${tl.name}", "§7Tier: ${if (maxed) "§6" else "§f"}${tl.tier} §8/ ${tl.cap}",
                "§7Kills: §f${full(tl.kills.toDouble())}", "§7Deaths: §c${full(tl.deaths.toDouble())}",
                tl.next?.let { "§7Next tier at: §f${full(it.toDouble())} §8(${full((it - tl.kills).toDouble())} more)" },
            ))
        }
    }
}
