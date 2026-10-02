package fishmod.features.pv.tabs

import fishmod.features.pv.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

object HomeTab : PvTab {
    override val id = "home"
    override val title = "Home"

    private var loadout = -1 // -1 = current gear, else wardrobe set index

    private val SKILL_ICONS by lazy {
        mapOf(
            "farming" to ItemStack(Items.GOLDEN_HOE), "mining" to ItemStack(Items.STONE_PICKAXE), "combat" to ItemStack(Items.STONE_SWORD),
            "foraging" to ItemStack(Items.JUNGLE_SAPLING), "fishing" to ItemStack(Items.FISHING_ROD), "enchanting" to ItemStack(Items.ENCHANTING_TABLE),
            "alchemy" to ItemStack(Items.BREWING_STAND), "taming" to ItemStack(Items.LEAD), "hunting" to ItemStack(Items.BOW),
            "carpentry" to ItemStack(Items.CRAFTING_TABLE), "runecrafting" to ItemStack(Items.MAGMA_CREAM), "social" to ItemStack(Items.EMERALD),
        )
    }
    private val LEVEL_ICON by lazy { ItemStack(Items.NETHER_STAR) }
    private val CATA_ICON by lazy { ItemStack(Items.WITHER_SKELETON_SKULL) }
    private val DATE = SimpleDateFormat("MMM d, yyyy")

    private const val ROW = 22
    private const val CARD_H = 112

    override fun height(c: PvCtx, area: PvRect): Int = 18 + 18 + 24 + 4 * ROW + 6 + statLines(c, area.w) * 12 + 8 + CARD_H

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        val m = c.member
        var y = area.y

        // Header: Stats for [rank] name on [profile]
        var x = area.x
        x += c.legacy("Stats for ", x, y + 1, PvCtx.S_XL, t.fg)
        val rank = c.result.player?.rankPrefix?.takeIf { it.length > 2 }
        if (rank != null) x += c.legacy("§l$rank", x, y + 1, PvCtx.S_XL) + 4
        val nm = m.name ?: PvData.nameFor(m.uuid)?.also { m.name = it } ?: c.result.name
        c.bold(nm, x, y + 1, t.fg, PvCtx.S_XL); x += c.textW(nm, PvCtx.S_XL) + 4
        x += c.legacy(" on ", x, y + 1, PvCtx.S_XL, t.mut)
        val pl = c.profile.cuteName + if (c.profile.modeIcon.isNotEmpty()) " " + c.profile.modeIcon else ""
        val pw = c.textW(pl, PvCtx.S_LG) + 22
        c.ring(x, y - 1, pw, 16, 8f, if (c.hovered(x, y - 1, pw, 16)) t.panel else t.panel2, t.line)
        c.bold(pl, x + 7, y + 3, t.fg, PvCtx.S_LG)
        fishmod.utils.rendering.UiRecorder.chevron(x + pw - 13f, y + 7f, true, t.mut)
        val px = x
        c.hit(x, y - 1, pw, 16) { c.screen.openProfileMenu(px + pw, y + 17) }
        y += 20

        // Member pills + status chips
        x = area.x
        if (c.profile.members.size > 1) for (mem in c.profile.members.values) {
            val n = mem.name ?: PvData.nameFor(mem.uuid) ?: mem.uuid.take(8)
            x += c.pill(x, y, n, mem.uuid == m.uuid) { c.screen.selectMember(mem.uuid) } + 4
        }
        val guild = when (c.result.guildStatus) {
            "ok" -> c.result.guild?.let { "Guild: §b${it.name}" + (it.tag?.let { tg -> " §7[$tg]" } ?: "") } ?: "No guild"
            "none" -> "No guild"; "loading" -> "Guild: …"; else -> "Guild: unavailable"
        }
        x += c.pill(x, y, guild, false) + 4
        val pl2 = c.result.player
        val status = when (c.result.playerStatus) {
            "ok" -> pl2?.lastLogin?.let { "Last seen §f" + ago(it) } ?: "Last seen ?"
            "loading" -> "Player: …"; else -> "Player data unavailable"
        }
        val sw = c.pill(x, y, status, false)
        if (pl2 != null) c.tip(x, y, sw, 13, listOfNotNull(
            "§fHypixel",
            pl2.networkLevel?.let { "§7Network level: §f${"%.2f".format(it)}" },
            pl2.karma?.let { "§7Karma: §d${full(it.toDouble())}" },
            pl2.achievementPoints?.let { "§7Achievement points: §e${full(it.toDouble())}" },
            pl2.firstLogin?.let { "§7First login: §f${DATE.format(Date(it))}" },
            pl2.lastLogin?.let { "§7Last login: §f${DATE.format(Date(it))}" },
        ))
        y += 18

        // SkyBlock level
        c.levelRow(area.x, y, area.w, LEVEL_ICON, "SkyBlock Level", m.sbLevel)
        y += 24

        // Skills: 3 columns
        val gap = 18
        val colW = (area.w - gap * 2) / 3
        for ((i, s) in m.skills.withIndex()) {
            val cx = area.x + (i % 3) * (colW + gap)
            val cy = y + (i / 3) * ROW
            c.levelRow(cx, cy, colW, SKILL_ICONS[s.key], s.name, s.level, xpKnown = !s.apiDisabled)
        }
        y += ((m.skills.size + 2) / 3) * ROW + 6

        y = statLine(c, area, y)
        y += 8

        // Cards row: Loadouts | Inventory | Combat
        val loW = 9 * 20 + 46
        val invW = 9 * PvCtx.SLOT + 18
        val combW = max(160, area.w - loW - invW - 24)
        loadoutCard(c, area.x, y, loW)
        inventoryCard(c, area.x + loW + 12, y, invW)
        combatCard(c, area.x + loW + invW + 24, y, combW)
    }

    private fun stats(c: PvCtx): List<Pair<String, List<String>>> {
        val m = c.member; val r = c.result
        val out = ArrayList<Pair<String, List<String>>>()
        m.firstJoin?.let { fj ->
            out += "Joined: §f${DATE.format(Date(fj))}" to listOf("§fFirst joined ${DATE.format(Date(fj))}", "§7Profile age: §f${(System.currentTimeMillis() - fj) / 86_400_000L} days")
        }
        out += "Purse: §6${fmt(m.purse)}" to listOf("§6${full(m.purse)} coins")
        val bank = c.profile.bank
        out += "Bank: §6${if (bank == null) "API off" else fmt(bank)}" to listOf(
            if (bank == null) "§cBanking API disabled" else "§6${full(bank)} coins",
            if (c.profile.members.size > 1) "§7Shared with ${c.profile.members.size} co-op members" else "§7Personal bank",
        )
        out += "Skill Avg: §f${"%.2f".format(m.skillAverage)}" to listOf(
            "§fSkill average", "§7Including cosmetic skills: §f${"%.2f".format(m.skillAverageCosmetic)}",
            "§7True avg (no progress): §f${"%.2f".format(m.skills.filter { it.key !in PvTables.COSMETIC_SKILLS }.map { it.level.level }.average())}",
        )
        m.fairySouls?.let { fs -> out += "Fairy Souls: §d$fs" to listOfNotNull("§d$fs §7collected", m.fairyExchanges?.let { "§7Exchanges: §f$it" }) }
        val nwLabel = when (r.networthStatus) { "ok" -> fmt(r.networth); "loading" -> "…"; else -> "unavailable" }
        out += "Networth: §6$nwLabel" to listOfNotNull(
            "§fNetworth", r.networth?.let { "§6${full(it)} coins" },
            r.networthProfile?.let { "§7Profile: §f$it" }, "§7Purse: §6${fmt(m.purse)}", "§7Bank: §6${fmt(c.profile.bank)}",
            if (r.networthProfile != null && r.networthProfile != c.profile.cuteName) "§8(networth is for the selected profile)" else null,
        )
        val acc = m.accessories
        val mp = acc.highestMagicalPower ?: acc.magicalPower
        out += "Magical Power: §b$mp" to listOfNotNull(
            "§fMagical Power: §b$mp", acc.selectedPower?.let { "§7Power: §f${PvData.pretty(it)}" },
            "§7Accessories: §f${acc.activeCount} active §8/ ${acc.list.size}",
            "§7Recombobulated: §f${acc.recombCount}", "§7Enriched: §f${acc.enrichedCount}",
        )
        return out
    }

    private fun statLines(c: PvCtx, w: Int): Int {
        var x = 0; var lines = 1
        for ((label, _) in stats(c)) { val lw = c.textW(label) + 16; if (x + lw > w && x > 0) { lines++; x = 0 }; x += lw }
        return lines
    }

    private fun statLine(c: PvCtx, area: PvRect, y0: Int): Int {
        var x = area.x; var y = y0
        for ((label, tip) in stats(c)) {
            val lw = c.textW(label) + 16
            if (x + lw > area.right && x > area.x) { x = area.x; y += 12 }
            val w = c.legacy(label, x, y, PvCtx.S_MD, c.theme.mut)
            c.rect(x, y + 9, w, 1, c.theme.line)
            c.tip(x, y - 1, w, 11, tip)
            x += lw
        }
        return y + 12
    }

    private fun loadoutCard(c: PvCtx, x: Int, y: Int, w: Int) {
        val t = c.theme; val m = c.member
        var cy = c.card(x, y, w, CARD_H, "Loadouts")
        val sets = (0 until m.wardrobeSetCount).filter { i -> m.wardrobeSet(i).any { it != null } }
        if (loadout >= 0 && loadout !in sets) loadout = -1
        var px = x + 9
        px += c.pill(px, cy, "Current", loadout == -1, PvCtx.S_XS, 11) { loadout = -1 } + 3
        for (i in sets) {
            val lbl = "W${i + 1}" + if (m.wardrobeEquipped == i + 1) "*" else ""
            val pw = c.textW(lbl, PvCtx.S_XS) + 12
            if (px + pw > x + w - 8) break
            px += c.pill(px, cy, lbl, loadout == i, PvCtx.S_XS, 11) { loadout = i } + 3
        }
        cy += 15
        c.text("Armor", x + 9, cy, t.mut, PvCtx.S_XS)
        c.text("Equip.", x + 9 + 4 * 20 + 6, cy, t.mut, PvCtx.S_XS)
        c.text("Pet", x + 9 + 8 * 20 + 12, cy, t.mut, PvCtx.S_XS)
        cy += 9
        val armor = if (loadout >= 0) m.wardrobeSet(loadout) else m.armor
        if (!m.inventoryApi) c.text("Inventory API off", x + 9, cy + 5, t.bad, PvCtx.S_SM)
        else {
            for (i in 0 until 4) c.item(armor.getOrNull(i), x + 9 + i * 20, cy)
            for (i in 0 until 4) c.item(m.equipment.items.getOrNull(i), x + 9 + 4 * 20 + 6 + i * 20, cy)
        }
        val pet = m.activePet
        val petX = x + 9 + 8 * 20 + 12
        c.panel(petX, cy, PvCtx.SLOT, PvCtx.SLOT, 3, t.slot)
        if (pet != null) {
            c.stack(PvPets.icon(pet), petX + 1, cy + 1)
            c.tip(petX, cy, PvCtx.SLOT, PvCtx.SLOT, PvPets.tooltip(pet))
        }
        cy += 24
        // Ability chips
        var chx = x + 9
        val acc = m.accessories
        val pwLabel = "Power §l${acc.selectedPower?.let { PvData.pretty(it) } ?: "None"}"
        val pW = c.pill(chx, cy, pwLabel, false, PvCtx.S_XS, 11)
        c.tip(chx, cy, pW, 11, listOf("§fAccessory power", "§7Magical Power: §b${acc.highestMagicalPower ?: acc.magicalPower}",
            "§7Unlocked powers: §f${acc.unlockedPowers.size}"))
        chx += pW + 3
        val hotm = m.hotm?.selectedAbility?.let { PvData.pretty(it) } ?: "None"
        val hW = c.pill(chx, cy, "HotM §l$hotm", false, PvCtx.S_XS, 11)
        c.tip(chx, cy, hW, 11, listOf("§fHeart of the Mountain ability", "§7$hotm"))
        chx = x + 9; cy += 14
        val hotf = m.hotf?.selectedAbility?.let { PvData.pretty(it) } ?: "None"
        val fW = c.pill(chx, cy, "HotF §l$hotf", false, PvCtx.S_XS, 11)
        c.tip(chx, cy, fW, 11, listOf("§fHeart of the Forest ability", "§7$hotf"))
        chx += fW + 3
        val tW = c.pill(chx, cy, "Tuning", false, PvCtx.S_XS, 11)
        val tun = acc.tuning.filterValues { it > 0 }
        c.tip(chx, cy, tW, 11, listOf("§fTuning Points") + if (tun.isEmpty()) listOf("§7None assigned") else tun.map { (k, v) -> "§7${PvData.pretty(k)}: §a+$v" })
    }

    private fun inventoryCard(c: PvCtx, x: Int, y: Int, w: Int) {
        val cy = c.card(x, y, w, CARD_H, "Inventory")
        val m = c.member
        if (!m.inventoryApi) { c.text("Inventory API disabled", x + 9, cy + 4, c.theme.bad, PvCtx.S_SM); return }
        val inv = m.inventory.items
        for (row in 0 until 4) for (col in 0 until 9) {
            // main 3 rows then hotbar, like in-game
            val idx = if (row < 3) 9 + row * 9 + col else col
            c.item(inv.getOrNull(idx), x + 9 + col * PvCtx.SLOT, cy + row * PvCtx.SLOT + (if (row == 3) 3 else 0))
        }
    }

    private fun combatCard(c: PvCtx, x: Int, y: Int, w: Int) {
        val t = c.theme; val m = c.member
        var cy = c.card(x, y, w, CARD_H, "Combat")
        val d = m.dungeons
        if (d == null) { c.text("No dungeon data", x + 9, cy, t.mut, PvCtx.S_SM) }
        else {
            c.levelRow(x + 9, cy, w - 18, CATA_ICON, "Catacombs", d.cata, listOfNotNull(
                d.selectedClass?.let { "§7Class: §f${PvData.pretty(it)}" },
                d.secrets?.let { "§7Secrets: §f${full(it.toDouble())}" },
                "§7Class average: §f${"%.2f".format(d.classAverage)}",
                d.highestFloor?.let { "§7Highest floor: §f$it" },
                "§7Total runs: §f${d.totalRuns}",
            ))
            cy += 26
            var cx = x + 9
            val classCol = mapOf("healer" to "§d", "mage" to "§b", "berserk" to "§c", "archer" to "§6", "tank" to "§a")
            for ((cls, lvl) in d.classes) {
                val sel = cls == d.selectedClass
                val lbl = "${classCol[cls]}${cls.take(1).uppercase()} §f${lvl.level}"
                val pw = c.textW(lbl, PvCtx.S_SM) + 12
                if (cx + pw > x + w - 6) break
                c.pill(cx, cy, lbl, false, PvCtx.S_SM, 13)
                if (sel) c.rect(cx + 4, cy + 11, pw - 8, 1, t.acc)
                c.tip(cx, cy, pw, 13, listOf("${classCol[cls]}${PvData.pretty(cls)} ${lvl.level}" + if (sel) " §a(selected)" else "",
                    "§7XP: §f${full(lvl.totalXp.toDouble())}",
                    if (lvl.maxed) "§6MAX" else "§7Progress: §a${"%.1f".format(lvl.progress * 100)}%"))
                cx += pw + 3
            }
        }
        cy += 18
        var sx = x + 9
        for (s in m.slayers) {
            val lbl = "${s.def.short} §l${s.level}"
            val pw = c.textW(lbl, PvCtx.S_SM) + 12
            if (sx + pw > x + w - 6) { sx = x + 9; cy += 16 }
            c.pill(sx, cy, lbl, false, PvCtx.S_SM, 13)
            val tiers = s.tierKills.mapIndexed { i, k -> "§7Tier ${i + 1}: §f$k" }
            c.tip(sx, cy, pw, 13, listOf("§f${s.def.name} ${s.level}", "§7XP: §f${full(s.xp.toDouble())}", "§7Kills: §f${s.totalKills}", "") + tiers)
            sx += pw + 3
        }
    }

    private fun ago(ms: Long): String {
        val d = (System.currentTimeMillis() - ms) / 1000
        return when {
            d < 3600 -> "${d / 60}m ago"
            d < 86400 -> "${d / 3600}h ago"
            else -> "${d / 86400}d ago"
        }
    }
}
