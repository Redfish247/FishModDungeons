package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.features.croesus.LootIcons
import fishmod.features.pv.*
import fishmod.utils.debug.FishDiag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object CollectionsTab : PvTab {
    override val id = "collections"
    override val title = "Collections"
    private val CATS = listOf("FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "RIFT")
    override val subTabs = CATS.map { PvData.pretty(it) }

    private class Coll(val key: String, val name: String, val maxTiers: Int, val tiers: LongArray)

    // Public resources endpoint (no key): categories, names, tier thresholds.
    @Volatile private var defs: Map<String, List<Coll>>? = null
    @Volatile private var status = "idle"

    private fun ensureDefs() {
        if (status != "idle") return
        status = "loading"
        runCatching {
            val req = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/resources/skyblock/collections"))
                .timeout(Duration.ofSeconds(10)).header("User-Agent", "FishMod/1.0").GET().build()
            fishmod.utils.Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete { r, err ->
                val parsed = if (err == null) FishDiag.guard("CollectionsTab.1", "collections resource parse failed") { parse(r.body()) } else null
                if (parsed == null) status = "failed" else { defs = parsed; status = "ok" }
            }
        }.onFailure { FishDiag.fail("CollectionsTab.2", "collections resource request failed", it); status = "failed" }
    }

    private fun parse(body: String): Map<String, List<Coll>> {
        val root = JsonParser.parseString(body).asJsonObject.obj("collections") ?: return emptyMap()
        val out = LinkedHashMap<String, List<Coll>>()
        for ((cat, el) in root.entrySet()) {
            val items = (el as? JsonObject).obj("items") ?: continue
            out[cat] = items.entrySet().mapNotNull { (k, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val tiers = o.arr("tiers")?.mapNotNull { (it as? JsonObject).long("amountRequired") }?.toLongArray() ?: LongArray(0)
                Coll(k, o.str("name") ?: PvData.pretty(k), o.int("maxTiers") ?: tiers.size, tiers)
            }
        }
        return out
    }

    private val icons = HashMap<String, ItemStack>()
    private fun icon(key: String): ItemStack = icons.getOrPut(key) {
        LootIcons.icon(key.replace(':', '-')) ?: LootIcons.icon(key) ?: LootIcons.icon(key.substringBefore(':')) ?: return ItemStack(Items.PAPER)
    }

    private val ROMAN = listOf("0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX")
    private const val ROW_H = 30
    private const val GAP = 8

    private fun cols(w: Int) = if (w >= 420) 3 else 2

    override fun height(c: PvCtx, area: PvRect): Int {
        val list = defs?.get(CATS.getOrNull(c.sub)) ?: return area.h
        val rows = (list.size + cols(area.w) - 1) / cols(area.w)
        return 16 + rows * (ROW_H + GAP)
    }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        ensureDefs()
        val t = c.theme
        val d = defs
        if (d == null) {
            val s = if (status == "failed") "Couldn't load collection data" else "Loading collections…"
            c.text(s, area.x, area.y, if (status == "failed") t.bad else t.mut)
            return
        }
        val cat = CATS.getOrNull(c.sub) ?: return
        val list = d[cat] ?: emptyList()
        val members = c.profile.members.values
        val totals = list.map { col -> members.sumOf { it.collections[col.key] ?: 0L } }
        val maxed = list.indices.count { i -> list[i].tiers.isNotEmpty() && totals[i] >= list[i].tiers.last() }
        val apiOff = members.none { it.raw.obj("collection") != null }
        c.text("$maxed / ${list.size} maxed" + if (apiOff) "  §c(collections API disabled)" else "", area.x, area.y, t.mut, PvCtx.S_SM)
        val n = cols(area.w)
        val w = (area.w - GAP * (n - 1)) / n
        for ((i, col) in list.withIndex()) {
            val x = area.x + (i % n) * (w + GAP)
            val y = area.y + 16 + (i / n) * (ROW_H + GAP)
            val amt = totals[i]
            val tier = col.tiers.count { amt >= it }
            val isMax = col.tiers.isNotEmpty() && tier >= col.tiers.size
            val accent = if (isMax) t.gold else t.acc
            c.panel(x, y, w, ROW_H, 6, t.panel, t.line)
            c.panel(x + 6, y + 6, 18, 18, 4, t.slot)
            c.stack(icon(col.key), x + 7, y + 7)
            val label = "${col.name} ${ROMAN.getOrElse(tier) { tier.toString() }}"
            c.bold(label, x + 30, y + 6, if (isMax) t.gold else t.fg, PvCtx.S_MD)
            val a = fmt(amt.toDouble())
            c.text(a, x + w - 7 - c.textW(a, PvCtx.S_SM), y + 7, t.mut, PvCtx.S_SM)
            val prev = if (tier == 0) 0L else col.tiers[tier - 1]
            val next = col.tiers.getOrNull(tier)
            val frac = if (next == null) 1.0 else (amt - prev).toDouble() / (next - prev).coerceAtLeast(1)
            c.bar(x + 30, y + 19, w - 37, 4, if (amt == 0L) 0.0 else frac, accent)
            val tip = ArrayList<String>()
            tip += "§f${col.name} ${ROMAN.getOrElse(tier) { tier.toString() }}" + if (isMax) " §6MAXED" else ""
            tip += "§7Collected: §f${full(amt.toDouble())}"
            if (next != null) {
                tip += "§7Next tier (${ROMAN.getOrElse(tier + 1) { "${tier + 1}" }}): §f${full(next.toDouble())}"
                tip += "§7Remaining: §e${full((next - amt).toDouble())}"
            }
            tip += "§7Tiers: §f$tier / ${col.maxTiers}"
            if (members.size > 1) {
                tip += ""
                tip += "§fCo-op contribution"
                for (mem in members.sortedByDescending { it.collections[col.key] ?: 0L }) {
                    val v = mem.collections[col.key] ?: 0L
                    val nm = mem.name ?: PvData.nameFor(mem.uuid) ?: mem.uuid.take(8)
                    val pct = if (amt > 0) " §8(${"%.1f".format(v * 100.0 / amt)}%)" else ""
                    tip += "§7$nm: §f${full(v.toDouble())}$pct"
                }
            }
            c.tip(x, y, w, ROW_H, tip)
        }
    }
}
