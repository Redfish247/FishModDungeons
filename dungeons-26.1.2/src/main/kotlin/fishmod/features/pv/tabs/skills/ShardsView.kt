package fishmod.features.pv.tabs.skills

import com.google.gson.JsonObject
import fishmod.features.pv.*
import fishmod.utils.rendering.UiRecorder
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max

// Attribute shards: levels from attributes.stacks (shards syphoned), owned counts from shards.owned.
internal object ShardsView {
    private class Chip(val def: AttrDef, val label: String, val w: Int, val level: Int, val tip: List<String>)
    private class Built(val all: List<Chip>, val unlocked: List<Chip>, val missing: List<Chip>, val maxed: Int, val header: String, val pills: Array<String>)

    private var filter = 0
    private var cached: Pair<JsonObject, Built>? = null
    private val ICON by lazy { ItemStack(Items.PRISMARINE_SHARD) }
    private const val CH = 18

    private fun build(c: PvCtx, raw: JsonObject): Built {
        val stacks = raw.obj("attributes", "stacks")
        val owned = HashMap<String, Int>()
        raw.arr("shards", "owned")?.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val t = o.str("type")?.lowercase() ?: return@forEach
            owned[t] = o.int("amount_owned") ?: 0
        }
        val chips = ArrayList<Chip>(AttrData.ALL.size)
        var maxed = 0
        for (d in AttrData.ALL) {
            val st = stacks.int(d.id) ?: 0
            val table = AttrData.LEVELS[d.rarity] ?: intArrayOf()
            val lv = table.count { st >= it }
            val isMax = table.isNotEmpty() && lv >= table.size
            if (isMax) maxed++
            val code = PvTables.RARITY_CODE[d.rarity] ?: "§7"
            val label = (if (lv > 0) code else "§8") + d.ability + " " + (if (isMax) "§6MAX" else if (lv > 0) "§f$lv" else "§8-")
            val tip = ArrayList<String>()
            tip += "$code${d.ability}"
            tip += "§8${d.name} Shard (${d.code}) · $code${d.rarity}"
            tip += ""
            tip += "§7Level: " + (if (isMax) "§6$lv §7(MAX)" else "§f$lv§8/10")
            tip += "§7Shards syphoned: §f$st§8/${table.lastOrNull() ?: "?"}"
            if (!isMax && lv < table.size) tip += "§7Next level at: §f${table[lv]}"
            tip += "§7Shards owned: §f${owned[d.shard] ?: 0}"
            chips += Chip(d, label, c.textW(label, PvCtx.S_SM) + 30, lv, tip)
        }
        val un = chips.filter { it.level > 0 }
        val miss = chips.filter { it.level == 0 }
        return Built(chips, un, miss, maxed, "${un.size}/${chips.size} unlocked · $maxed maxed",
            arrayOf("All ${chips.size}", "Unlocked ${un.size}", "Missing ${miss.size}"))
    }

    private fun rows(list: List<Chip>, w: Int): Int {
        if (list.isEmpty()) return 1
        var x = 0; var r = 1
        for (ch in list) { if (x + ch.w > w && x > 0) { r++; x = 0 }; x += ch.w + 4 }
        return r
    }

    fun card(c: PvCtx, area: PvRect, y: Int): Int {
        val raw = c.member.raw
        val cc = cached
        val b = if (cc != null && cc.first === raw) cc.second else build(c, raw).also { cached = raw to it }
        val t = c.theme
        val list = when (filter) { 1 -> b.unlocked; 2 -> b.missing; else -> b.all }
        val inner = area.w - 18
        val h = 21 + 18 + rows(list, inner) * (CH + 3) + 6
        c.card(area.x, y, area.w, h, null)
        val title = "Attribute Shards"
        c.bold(title, area.x + 9, y + 7, t.fg, PvCtx.S_LG)
        c.text(b.header, area.x + 9 + Math.ceil(UiRecorder.textWidthBold(title, PvCtx.S_LG).toDouble()).toInt() + 8, y + 8, t.mut, PvCtx.S_SM)
        var px = area.x + 9
        for (i in 0 until 3) { val idx = i; px += c.pill(px, y + 21, b.pills[i], filter == i) { filter = idx } + 4 }
        var cx = area.x + 9; var cy = y + 39
        if (list.isEmpty()) c.text("Nothing here.", cx, cy + 4, t.mut, PvCtx.S_SM)
        for (ch in list) {
            if (cx + ch.w > area.x + 9 + inner && cx > area.x + 9) { cx = area.x + 9; cy += CH + 3 }
            val maxed = ch.level >= 10
            c.ring(cx, cy, ch.w, CH, 5f, t.panel2, if (maxed) t.gold else t.line)
            c.stack(ICON, cx + 1, cy + 1)
            c.legacy(ch.label, cx + 20, cy + 6, PvCtx.S_SM)
            c.tip(cx, cy, ch.w, CH, ch.tip)
            cx += ch.w + 4
        }
        return y + h + 8
    }
}
