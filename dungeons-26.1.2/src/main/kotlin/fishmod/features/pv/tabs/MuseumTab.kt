package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import fishmod.features.pv.*
import fishmod.utils.debug.FishDiag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import kotlin.math.max

object MuseumTab : PvTab {
    override val id = "museum"
    override val title = "Museum"

    private const val GAP = 10
    private const val CELL = 20
    private val DATE = SimpleDateFormat("MMM d, yyyy")
    private val ARMOR = Regex("(HELMET|CHESTPLATE|LEGGINGS|BOOTS|HAT|HOOD|MASK|CROWN)")

    private class Entry(val key: String, val item: PvItem?, val pieces: Int, val donated: Long?, val borrowing: Boolean)
    private class Parsed(val weapons: List<Entry>, val armor: List<Entry>, val special: List<Entry>)
    private val cache = IdentityHashMap<JsonObject, Parsed>()

    private fun parse(raw: JsonObject): Parsed = cache.getOrPut(raw) {
        val weapons = ArrayList<Entry>(); val armor = ArrayList<Entry>(); val special = ArrayList<Entry>()
        raw.obj("items")?.entrySet()?.forEach { (k, v) ->
            val o = runCatching { v.asJsonObject }.getOrNull() ?: return@forEach
            val items = FishDiag.guard("MuseumTab.1", "museum decode failed $k") { PvItem.decode(o.str("items", "data")) }.orEmpty().filterNotNull()
            val e = Entry(k, items.firstOrNull(), items.size, o.long("donated_time"), o.bool("borrowing") == true)
            if (items.size > 1 || ARMOR.containsMatchIn(k)) armor += e else weapons += e
        }
        raw.arr("special")?.forEachIndexed { i, v ->
            val o = runCatching { v.asJsonObject }.getOrNull() ?: return@forEachIndexed
            val items = FishDiag.guard("MuseumTab.2", "museum special decode failed") { PvItem.decode(o.str("items", "data")) }.orEmpty().filterNotNull()
            special += Entry("special_$i", items.firstOrNull(), items.size, o.long("donated_time"), false)
        }
        val byRarity = compareBy<Entry> { PvData.RARITY_ORDER.indexOf(it.item?.rarity ?: "").let { r -> -r } }
        Parsed(weapons.sortedWith(byRarity), armor.sortedWith(byRarity), special.sortedWith(byRarity))
    }

    private fun sections(p: Parsed): List<Pair<String, List<Entry>>> {
        val all = p.weapons + p.armor + p.special
        val rarities = all.groupBy { it.item?.rarity ?: "UNKNOWN" }
        return listOf("Weapons" to p.weapons, "Armor Sets" to p.armor, "Special" to p.special).filter { it.second.isNotEmpty() } +
            (if (rarities.isNotEmpty()) listOf("Rarities" to emptyList<Entry>()) else emptyList())
    }

    private fun gridH(n: Int, w: Int): Int { val per = max(1, (w - 18) / CELL); return 21 + ((n + per - 1) / per) * CELL + 6 }

    override fun height(c: PvCtx, area: PvRect): Int {
        val mu = PvData.museumFor(c.profile, c.member.uuid) ?: return area.h
        val p = parse(mu.raw)
        return 18 + sections(p).sumOf { (n, l) -> (if (n == "Rarities") 21 + 12 * rarityRows(p).size + 6 else gridH(l.size, area.w)) + GAP }
    }

    private fun rarityRows(p: Parsed) = (p.weapons + p.armor + p.special).groupingBy { it.item?.rarity ?: "UNKNOWN" }.eachCount()
        .entries.sortedByDescending { PvData.RARITY_ORDER.indexOf(it.key) }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        PvData.ensureProfileExtras(c.profile)
        val mu = PvData.museumFor(c.profile, c.member.uuid)
        if (mu == null) {
            val msg = when (c.profile.museumStatus) { "loading" -> "Loading museum…"; "ok" -> "Nothing donated to the museum"; else -> "Museum unavailable" }
            c.text(msg, area.x, area.y + 4, t.mut); return
        }
        val p = parse(mu.raw)
        var y = area.y
        // Progress statline
        val total = mu.donated + mu.special
        val milestone = mu.raw.int("milestone") ?: 0
        var x = area.x
        x += c.legacy("§7Appraised value: §6${mu.value?.let { fmt(it.toDouble()) } ?: "?"}", x, y, PvCtx.S_MD) + 14
        c.tip(area.x, y - 1, x - area.x, 11, listOf("§fMuseum value", "§6${full(mu.value?.toDouble())} coins", if (mu.appraisal == true) "§aAppraised" else "§7Not appraised"))
        val dx = x
        x += c.legacy("§7Donated: §f${mu.donated} items §8+ §d${mu.special} special", x, y, PvCtx.S_MD) + 14
        c.tip(dx, y - 1, x - dx, 11, listOf("§fDonations", "§7Weapons: §f${p.weapons.size}", "§7Armor sets: §f${p.armor.size}", "§7Special: §f${p.special.size}",
            "§7Pieces: §f${(p.weapons + p.armor).sumOf { it.pieces }}"))
        val mx = x
        x += c.legacy("§7Milestone: §b$milestone", x, y, PvCtx.S_MD)
        c.tip(mx, y - 1, x - mx, 11, listOf("§fMuseum milestone §b$milestone", "§7Claimed reward tier"))
        y += 14
        y += 4
        for ((name, list) in sections(p)) {
            if (name == "Rarities") {
                val rows = rarityRows(p)
                val h = 21 + 12 * rows.size + 6
                var ry = c.card(area.x, y, area.w, h, name)
                for ((r, n) in rows) {
                    c.legacy("${PvTables.RARITY_CODE[r] ?: "§7"}${PvData.pretty(r)}", area.x + 9, ry, PvCtx.S_SM)
                    val v = "$n"; c.text(v, area.right - 9 - c.textW(v, PvCtx.S_SM), ry, t.fg, PvCtx.S_SM)
                    ry += 12
                }
                y += h + GAP; continue
            }
            val h = gridH(list.size, area.w)
            val cy = c.card(area.x, y, area.w, h, "$name §8${list.size}")
            val per = max(1, (area.w - 18) / CELL)
            for ((i, e) in list.withIndex()) {
                val ix = area.x + 9 + (i % per) * CELL; val iy = cy + (i / per) * CELL
                c.item(e.item, ix, iy)
                if (e.item != null) c.tip(ix, iy, PvCtx.SLOT, PvCtx.SLOT, e.item.tooltip + listOfNotNull("",
                    e.donated?.let { "§7Donated: §f${DATE.format(Date(it))}" },
                    if (e.pieces > 1) "§7Pieces: §f${e.pieces}" else null,
                    if (e.borrowing) "§eCurrently borrowed" else null))
            }
            y += h + GAP
        }
    }
}
