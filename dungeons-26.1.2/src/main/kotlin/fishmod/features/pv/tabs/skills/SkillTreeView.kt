package fishmod.features.pv.tabs.skills

import com.google.gson.JsonObject
import fishmod.features.pv.*
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import fishmod.utils.rendering.UiRecorder
import kotlin.math.max

// Tree node (layout mirrors skyblock-pv / meowdding-repo). type: 0 perk, 1 ability, 2 core, 3 unlevelable, 4 tier.
internal class TNode(
    val id: String, val name: String, val type: Int, val x: Int, val y: Int, val max: Int,
    val costKind: String?, val costFormula: String?, val rewardKeys: Array<String>, val rewardFormulas: Array<String>,
    val tooltip: Array<String>, val coreLevels: Array<Array<String>> = emptyArray(),
    val coreCostKinds: Array<String> = emptyArray(), val coreCosts: LongArray = LongArray(0),
)

// Tiny arithmetic evaluator for repo formulas: + - * / ^, parens, floor/ceil/round/min/max, variables.
internal object Formula {
    private val cache = HashMap<String, (Map<String, Double>) -> Double>()

    fun eval(src: String, vars: Map<String, Double>): Double = runCatching { cache.getOrPut(src) { Parser(src).parse() }(vars) }.getOrDefault(0.0)

    private class Parser(val s: String) {
        var i = 0
        fun parse(): (Map<String, Double>) -> Double { val e = expr(); return e }
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): (Map<String, Double>) -> Double {
            var l = term()
            while (true) { ws(); if (i >= s.length) return l
                val op = s[i]; if (op != '+' && op != '-') return l; i++
                val a = l; val b = term(); l = if (op == '+') { v -> a(v) + b(v) } else { v -> a(v) - b(v) } }
        }
        fun term(): (Map<String, Double>) -> Double {
            var l = pow()
            while (true) { ws(); if (i >= s.length) return l
                val op = s[i]; if (op != '*' && op != '/') return l; i++
                val a = l; val b = pow(); l = if (op == '*') { v -> a(v) * b(v) } else { v -> a(v) / b(v) } }
        }
        fun pow(): (Map<String, Double>) -> Double {
            val a = unary(); ws()
            if (i < s.length && s[i] == '^') { i++; val b = pow(); return { v -> Math.pow(a(v), b(v)) } }
            return a
        }
        fun unary(): (Map<String, Double>) -> Double { ws(); if (i < s.length && s[i] == '-') { i++; val a = unary(); return { v -> -a(v) } }; return atom() }
        fun atom(): (Map<String, Double>) -> Double {
            ws()
            if (s[i] == '(') { i++; val e = expr(); ws(); i++; return e }
            val st = i
            if (s[i].isDigit() || s[i] == '.') { while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++; val d = s.substring(st, i).toDouble(); return { d } }
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            val name = s.substring(st, i); ws()
            if (i < s.length && s[i] == '(') {
                i++; val args = ArrayList<(Map<String, Double>) -> Double>()
                while (true) { args += expr(); ws(); if (s[i] == ',') { i++; continue }; i++; break }
                return when (name) {
                    "floor" -> { v -> Math.floor(args[0](v)) }
                    "ceil" -> { v -> Math.ceil(args[0](v)) }
                    "round" -> { v -> Math.round(args[0](v)).toDouble() }
                    "min" -> { v -> minOf(args[0](v), args[1](v)) }
                    "max" -> { v -> maxOf(args[0](v), args[1](v)) }
                    else -> { v -> args[0](v) }
                }
            }
            return { v -> v[name] ?: 0.0 }
        }
    }
}

internal class TreeKind(
    val title: String, val skill: String, val tree: String, val core: String, val nodes: Array<TNode>, val xpTable: LongArray,
    val tier: Item, val tierNext: Item, val tierLocked: Item,
    val coreMax: Item, val coreUnlocked: Item, val coreLeveling: Item, val coreLocked: Item,
    val abSelected: Item, val abUnlocked: Item, val abLocked: Item,
    val nDisabled: Item, val nMax: Item, val nUnlocked: Item, val nLocked: Item,
)

internal object SkillTreeView {
    val HOTM = TreeKind("Heart of the Mountain", "mining", "mountain", "core_of_the_mountain", TreeData.HOTM,
        longArrayOf(0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000),
        Items.LIME_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
        Items.DIAMOND_BLOCK, Items.COPPER_BLOCK, Items.REDSTONE_BLOCK, Items.BEDROCK,
        Items.EMERALD_BLOCK, Items.REDSTONE_BLOCK, Items.COAL_BLOCK,
        Items.REDSTONE, Items.DIAMOND, Items.EMERALD, Items.COAL)
    val HOTF = TreeKind("Heart of the Forest", "foraging", "forest", "center_of_the_forest", TreeData.HOTF,
        longArrayOf(0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 547_000),
        Items.LIME_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
        Items.OAK_WOOD, Items.STRIPPED_BIRCH_WOOD, Items.STRIPPED_OAK_WOOD, Items.STRIPPED_PALE_OAK_WOOD,
        Items.OAK_SAPLING, Items.CHERRY_SAPLING, Items.PALE_OAK_SAPLING,
        Items.STRIPPED_MANGROVE_LOG, Items.OAK_LOG, Items.STRIPPED_OAK_LOG, Items.PALE_OAK_BUTTON)

    private class Cell(val col: Int, val row: Int, val stack: ItemStack, val label: String?, val tip: List<String>, val on: Boolean = false)
    private class Built(val cells: List<Cell>, val rows: Int, val tierTxt: String, val side: List<Pair<String, String>>, val sideTips: List<List<String>>)

    private val cache = HashMap<String, Pair<JsonObject, Built>>()

    private const val SLOT = 18
    private const val PITCH = 20
    private const val TIER_GAP = 8

    private fun num(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e15) "%,d".format(d.toLong()) else "%.2f".format(d).trimEnd('0').trimEnd('.')

    private fun pretty(s: String) = s.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private fun build(raw: JsonObject, k: TreeKind): Built {
        val st = raw.obj("skill_tree")
        val slot = st.int("selected_skill_tree_slot", k.skill) ?: 1
        val sfx = if (slot > 1) "_$slot" else ""
        val nodesObj = st.obj("nodes", k.skill + sfx) ?: st.obj("nodes", k.skill)
        val levels = HashMap<String, Int>(); val disabled = HashSet<String>()
        nodesObj?.entrySet()?.forEach { (key, v) ->
            if (key.startsWith("toggle_")) { if (runCatching { !v.asBoolean }.getOrDefault(false)) disabled += key.removePrefix("toggle_") }
            else runCatching { levels[key] = v.asInt }
        }
        val selected = st.str("selected_ability", k.skill + sfx)
        val xp = st.long("experience", k.skill) ?: 0L
        val tierLv = k.xpTable.count { xp >= it }
        val maxTier = k.xpTable.size
        val coreLv = levels[k.core] ?: 0
        val vars = HashMap<String, Double>()
        val cells = ArrayList<Cell>()
        var maxed = 0; var unlocked = 0; var tokensTotal = 0
        val powderSpent = HashMap<String, Long>()
        val rows = k.nodes.maxOf { it.y } + 1
        val tokenRe = Regex("(\\d+) Token")
        for (n in k.nodes) {
            val row = rows - 1 - n.y
            val col = if (n.type == 4) 0 else n.x + 1
            val tip = ArrayList<String>()
            if (n.type == 4) {
                val tierN = n.y + 1
                val done = tierLv >= tierN; val next = tierLv + 1 == tierN
                if (done) n.tooltip.forEach { l -> tokenRe.find(l.replace(Regex("§."), ""))?.let { tokensTotal += it.groupValues[1].toInt() } }
                tip += (if (done) "§a" else if (next) "§e" else "§c") + n.name
                if (next) {
                    val into = xp - k.xpTable[tierLv - 1].coerceAtLeast(0); val need = k.xpTable[tierLv] - k.xpTable[tierLv - 1]
                    tip += ""; tip += "§7Progress: §e${num(Math.floor(into * 1000.0 / need) / 10)}%"; tip += "§e${num(into.toDouble())}§6/§e${num(need.toDouble())} XP"
                }
                tip += ""; tip += "§7Rewards"; tip.addAll(n.tooltip); tip += ""
                tip += if (done) "§a§lUNLOCKED" else if (next) "§c§lLOCKED" else "§cRequires Tier ${n.y}"
                cells += Cell(col, row, ItemStack(if (done) k.tier else if (next) k.tierNext else k.tierLocked), null, tip, done)
                continue
            }
            val raw0 = levels[n.id]
            val lv = raw0 ?: -1
            val has = lv > 0
            val off = if (n.type == 1) selected != n.id else disabled.contains(n.id)
            val isMax = has && lv >= n.max
            if (has) unlocked++
            if (isMax) maxed++
            val item = when (n.type) {
                2 -> when { isMax -> k.coreMax; lv < 0 -> k.coreLocked; lv == 1 -> k.coreUnlocked; else -> k.coreLeveling }
                1 -> when { selected == n.id -> k.abSelected; has -> k.abUnlocked; else -> k.abLocked }
                else -> when { has && off -> k.nDisabled; !has -> k.nLocked; isMax -> k.nMax; else -> k.nUnlocked }
            }
            tip += (if (!has || (off && n.type != 2)) "§c" else "§a") + n.name
            if (n.type == 0 || n.type == 2) {
                tip += if (isMax) "§7Level ${max(lv, 0)} §6(MAX)" else "§7Level ${max(lv, 0)}§8/${n.max}"
                tip += ""
            }
            // Description with formulas filled at the current (or first) level.
            vars.clear()
            val effLv = if (n.type == 1) (if (coreLv >= 2) 2.0 else 1.0) else max(lv, 1).toDouble()
            vars["level"] = effLv; vars["effectiveLevel"] = effLv; vars["hotmLevel"] = tierLv.toDouble()
            if (n.type == 2) {
                for ((i, rw) in n.coreLevels.withIndex()) {
                    val reached = lv >= i + 1
                    val c = if (reached) "§a✔ " else "§8✖ "
                    rw.forEachIndexed { j, l -> tip += (if (j == 0) "$c§7Lv ${i + 1}: " else "      ") + l }
                }
                val nextCost = n.coreCosts.getOrNull(max(lv, 0))
                if (!isMax && nextCost != null && nextCost > 0) { tip += ""; tip += "§7Next level: §f${num(nextCost.toDouble())} ${pretty(n.coreCostKinds[max(lv, 0)].lowercase())}" }
                for (i in 1 until max(lv, 0).coerceAtMost(n.coreCosts.size)) {
                    val kd = n.coreCostKinds[i]; if (kd.isNotEmpty()) powderSpent[kd] = (powderSpent[kd] ?: 0) + n.coreCosts[i]
                }
            } else {
                for (l in n.tooltip) {
                    var s = l
                    for (ri in n.rewardKeys.indices) s = s.replace("%${n.rewardKeys[ri]}%", num(Formula.eval(n.rewardFormulas[ri], vars)))
                    tip += s
                }
            }
            if (n.type == 0 && n.costFormula != null && n.costKind != null) {
                var spent = 0L
                for (l in 1 until max(lv, 1)) { vars["level"] = l.toDouble(); spent += Formula.eval(n.costFormula, vars).toLong() }
                var total = 0L
                for (l in 1 until n.max) { vars["level"] = l.toDouble(); total += Formula.eval(n.costFormula, vars).toLong() }
                if (has) powderSpent[n.costKind] = (powderSpent[n.costKind] ?: 0) + spent
                val kind = pretty(n.costKind.lowercase())
                tip += ""
                if (!isMax && has) { vars["level"] = lv.toDouble(); tip += "§7Cost to next: §f${num(Formula.eval(n.costFormula, vars))} $kind" }
                tip += "§7$kind spent: §f${num(spent.toDouble())}§8/${num(total.toDouble())}"
            }
            if (has && n.type != 2) { tip += ""; tip += if (n.type == 1) (if (off) "§8Not selected" else "§a§lSELECTED") else if (off) "§c§lDISABLED" else "§a§lENABLED" }
            if (!has) { tip += ""; tip += "§cLocked" }
            if (n.type == 2 && has) n.coreLevels.take(lv).forEach { rw -> rw.forEach { l -> tokenRe.find(l.replace(Regex("§."), ""))?.let { tokensTotal += it.groupValues[1].toInt() } } }
            cells += Cell(col, row, ItemStack(item), if (lv > 1) "$lv" else null, tip, has)
        }
        val tokensSpent = st.int("tokens_spent", k.tree + sfx) ?: 0
        val side = ArrayList<Pair<String, String>>(); val sideTips = ArrayList<List<String>>()
        fun add(l: String, v: String, t: List<String> = emptyList()) { side += l to v; sideTips += t }
        add("Tier", "$tierLv / $maxTier", listOf("§7Tree XP: §f${num(xp.toDouble())}"))
        add("Tokens spent", "$tokensSpent")
        add("Tokens available", "${max(0, tokensTotal - tokensSpent)}", listOf("§7Earned: §f$tokensTotal"))
        add("Perks unlocked", "$unlocked")
        add("Perks maxed", "$maxed")
        add("Selected ability", selected?.let { id -> k.nodes.firstOrNull { it.id == id }?.name ?: pretty(id) } ?: "None")
        if (k.skill == "mining") {
            val core = raw.obj("mining_core")
            for ((p, col) in listOf("mithril" to "§2", "gemstone" to "§d", "glacite" to "§b")) {
                val sp = core.long("powder_spent_$p" + (if (slot > 1) "_$slot" else "")) ?: 0L
                val av = core.long("powder_$p") ?: 0L
                add("${pretty(p)} spent", "$col${fmt(sp.toDouble())}", listOf("$col${pretty(p)} Powder", "§7Spent: §f${num(sp.toDouble())}", "§7Available: §f${num(av.toDouble())}", "§7Total: §f${num((sp + av).toDouble())}"))
            }
        } else {
            val wh = raw.obj("foraging_core", "whispers")
            for ((p, col) in listOf("forest" to "§2", "desert" to "§6")) {
                val sp = wh.long(p, "$slot", "spent") ?: 0L
                val tot = wh.long(p, "total") ?: 0L
                add("${pretty(p)} whispers", "$col${fmt(sp.toDouble())}", listOf("$col${pretty(p)} Whispers", "§7Spent: §f${num(sp.toDouble())}", "§7Available: §f${num(tot.toDouble())}"))
            }
        }
        if (slot > 1) add("Loadout", "Slot $slot")
        return Built(cells, rows, "Tier $tierLv/$maxTier", side, sideTips)
    }

    // Draws the whole card; returns the bottom y.
    fun card(c: PvCtx, area: PvRect, y: Int, k: TreeKind): Int {
        val raw = c.member.raw
        val hit = cache[k.skill]
        val b = if (hit != null && hit.first === raw) hit.second else build(raw, k).also { cache[k.skill] = raw to it }
        val t = c.theme
        val gridH = b.rows * PITCH
        val sideH = b.side.size * 13 + 4
        val h = 24 + max(gridH, sideH) + 8
        c.card(area.x, y, area.w, h, null)
        c.bold(k.title, area.x + 9, y + 7, t.fg, PvCtx.S_LG)
        c.text(b.tierTxt, area.x + 9 + Math.ceil(UiRecorder.textWidthBold(k.title, PvCtx.S_LG).toDouble()).toInt() + 8, y + 8, t.mut, PvCtx.S_SM)
        val gx = area.x + 10; val gy = y + 24
        for (cell in b.cells) {
            val x = gx + (if (cell.col == 0) 0 else PITCH + TIER_GAP + (cell.col - 1) * PITCH)
            val cy = gy + cell.row * PITCH
            // Vanilla-layer fill: an overlay rect would paint over the item icon.
            PvCtx.smoothRect(c.g, x, cy, SLOT, SLOT, 3f, if (cell.on) 0x5055FF55 else t.slot, c.dp)
            c.stack(cell.stack, x + 1, cy + 1)
            if (cell.label != null) c.g.itemDecorations(c.font, cell.stack, x + 1, cy + 1, cell.label)
            c.tip(x, cy, SLOT, SLOT, cell.tip)
        }
        val sx = gx + PITCH + TIER_GAP + 7 * PITCH + 14
        val sw = area.right - sx - 10
        var sy = gy + 2
        val vx = sx + (b.side.maxOfOrNull { c.textW(it.first, PvCtx.S_SM) } ?: 0) + 8
        for (i in b.side.indices) {
            val (l, v) = b.side[i]
            c.text(l, sx, sy, t.mut, PvCtx.S_SM)
            c.legacy(v, vx, sy, PvCtx.S_SM, t.fg)
            c.tip(sx, sy - 1, sw, 12, b.sideTips[i])
            sy += 13
        }
        if (b.cells.none { it.label != null } && raw.obj("skill_tree") == null) c.text("No tree data", sx, sy + 4, t.bad, PvCtx.S_SM)
        return y + h + 8
    }
}
