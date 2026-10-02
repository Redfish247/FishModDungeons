package fishmod.features.pv.tabs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.features.pv.*
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import kotlin.math.max

// Areas/families/brackets/icons come from NEU's constants/bestiary.json (trimmed into /data/fishmod_bestiary.json).
object BestiaryTab : PvTab {
    override val id = "bestiary"
    override val title = "Bestiary"

    private const val HEAD = 22
    private const val TMIN_W = 124
    private const val TH = 32
    private const val TGAP = 6
    private const val BAR_W = 90
    private val open = HashSet<String>()

    private class Icon(val tex: String?, val item: String?) {
        private var st: ItemStack? = null
        fun stack(): ItemStack = st ?: build().also { st = it }
        private fun build(): ItemStack {
            val tex = tex
            if (tex != null) {
                val s = ItemStack(Items.PLAYER_HEAD)
                runCatching {
                    val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", tex))
                    val gp = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(tex.toByteArray()), "fmbe", com.mojang.authlib.properties.PropertyMap(props))
                    s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gp))
                }
                return s
            }
            val it = item?.let { Identifier.tryParse("minecraft:$it") }?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            return ItemStack(if (it == null || it == Items.AIR) Items.BOOK else it)
        }
    }

    private class Fam(val name: String, val thresholds: LongArray, val cap: Long, val mobs: List<String>, val icon: Icon) {
        val maxTier = thresholds.count { it <= cap }.coerceAtLeast(1)
    }
    private class Area(val name: String, val icon: Icon, val fams: List<Fam>)

    private val AREAS: List<Area> by lazy {
        FishDiag.guard("PvBestiary.1", "failed to load bundled fishmod_bestiary.json") {
            val root = javaClass.getResourceAsStream("/data/fishmod_bestiary.json")!!.reader().use { JsonParser.parseReader(it).asJsonObject }
            fun arr(o: JsonObject?, k: String) = o?.getAsJsonArray(k)?.map { it.asLong }?.toLongArray()
            val brackets = root.getAsJsonObject("brackets"); val sets = root.getAsJsonObject("sets")
            fun icon(o: JsonObject) = Icon(o.get("tex")?.asString, o.get("item")?.asString)
            root.getAsJsonArray("areas").map { ae ->
                val a = ae.asJsonObject
                Area(a.get("name").asString, icon(a.getAsJsonObject("icon")), a.getAsJsonArray("fams").map { fe ->
                    val f = fe.asJsonObject
                    val b = f.get("b").asInt.toString()
                    val th = (f.get("set")?.asString?.let { arr(sets.getAsJsonObject(it), b) } ?: arr(brackets, b)) ?: LongArray(0)
                    Fam(f.get("n").asString, th, f.get("cap").asLong, f.getAsJsonArray("mobs").map { it.asString }, icon(f))
                })
            }
        } ?: emptyList()
    }
    private val OTHER_ICON = Icon(null, "book")
    private val BOOK by lazy { ItemStack(Items.WRITABLE_BOOK) }
    private val OTHER_THRESH = longArrayOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
    private val IGNORED = setOf("last_killed_mob")

    private class Tile(val name: String, val icon: Icon, val kills: Long, val tier: Int, val cap: Int, frac: Double, val tip: List<String>) {
        val maxed = tier >= cap
        val frac = if (maxed) 1.0 else frac
        val tierTxt = "$tier / $cap"
        val killsTxt = "§7Kills §f${fmt(kills.toDouble())}"
        var shown = name
        var shownW = -1
    }
    private class Sec(val name: String, val icon: Icon, val tiles: List<Tile>) {
        val maxed = tiles.count { it.maxed }
        val label = "$maxed / ${tiles.size} maxed"
        val frac = if (tiles.isEmpty()) 0.0 else maxed.toDouble() / tiles.size
        val done = tiles.isNotEmpty() && maxed == tiles.size
    }
    private class Data(val secs: List<Sec>, val lvl: PvTables.Level, val lvlTip: List<String>)

    private var cacheKey: Any? = null
    private var cache: Data? = null

    private fun tile(name: String, icon: Icon, kills: Long, deaths: Long, th: LongArray, cap: Long, maxTier: Int): Tile {
        val eff = minOf(kills, cap)
        val t = minOf(maxTier, th.count { eff >= it })
        val prev = if (t == 0) 0L else th[t - 1]
        val next = if (t < maxTier && t < th.size) th[t] else null
        val frac = if (next == null) 1.0 else (kills - prev).toDouble() / (next - prev).coerceAtLeast(1)
        val tip = listOfNotNull(
            "§f$name", "§7Tier: ${if (t >= maxTier) "§6" else "§f"}$t §8/ $maxTier",
            "§7Kills: §f${full(kills.toDouble())}", "§7Deaths: §c${full(deaths.toDouble())}",
            next?.let { "§7Next tier at: §f${full(it.toDouble())} §8(${full((it - kills).toDouble())} more)" },
            if (t >= maxTier) "§6Maxed" else null,
        )
        return Tile(name, icon, kills, t, maxTier, frac, tip)
    }

    private fun data(c: PvCtx): Data {
        val m = c.member
        cache?.let { if (cacheKey === m) return it }
        val kills = m.bestiaryKills; val deaths = m.bestiaryDeaths
        val used = HashSet<String>()
        val secs = ArrayList<Sec>()
        for (a in AREAS) {
            secs += Sec(a.name, a.icon, a.fams.map { f ->
                var k = 0L; var d = 0L
                for (id in f.mobs) { k += kills[id] ?: 0L; d += deaths[id] ?: 0L; used += id }
                tile(f.name, f.icon, k, d, f.thresholds, f.cap, f.maxTier)
            })
        }
        // Mobs Hypixel tracks but NEU doesn't place in a family yet.
        val other = kills.filterKeys { it !in used && it !in IGNORED }.entries.sortedByDescending { it.value }
        if (other.isNotEmpty()) secs += Sec("Other", OTHER_ICON, other.map { (k, v) ->
            tile(PvData.pretty(k.replace(Regex("_\\d+$"), "")), OTHER_ICON, v, deaths[k] ?: 0L, OTHER_THRESH, OTHER_THRESH.last(), OTHER_THRESH.size)
        })
        val counted = secs.filter { it.name != "Other" }
        val tiers = counted.sumOf { s -> s.tiles.sumOf { it.tier } }
        val maxTiers = counted.sumOf { s -> s.tiles.sumOf { it.cap } }
        val lvl = PvTables.Level(tiers / 10, (tiers % 10) / 10.0, (tiers % 10).toLong(), 10, tiers >= maxTiers, maxTiers / 10, tiers.toLong())
        val tip = listOf("§7Family tiers: §f$tiers §8/ $maxTiers",
            "§7Families maxed: §f${counted.sumOf { it.maxed }} §8/ ${counted.sumOf { it.tiles.size }}")
        val out = Data(secs, lvl, tip)
        cacheKey = m; cache = out
        return out
    }

    private fun perRow(w: Int) = max(1, (w - 18 + TGAP) / (TMIN_W + TGAP))
    private fun bodyH(s: Sec, w: Int) = ((s.tiles.size + perRow(w) - 1) / perRow(w)) * (TH + TGAP) + 4

    override fun height(c: PvCtx, area: PvRect): Int =
        24 + 18 + data(c).secs.sumOf { s -> HEAD + 4 + if (s.name in open) bodyH(s, area.w) else 0 }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val t = c.theme
        val d = data(c)
        val secs = d.secs
        var y = area.y

        c.levelRow(area.x, y, area.w, BOOK, "Bestiary Milestone", d.lvl, d.lvlTip)
        y += 24

        var px = area.x
        px += c.pill(px, y, "Expand all", false) { secs.forEach { open += it.name } } + 4
        c.pill(px, y, "Collapse all", false) { open.clear() }
        y += 18

        for (s in secs) {
            val isOpen = s.name in open
            val h = HEAD + if (isOpen) bodyH(s, area.w) else 0
            c.panel(area.x, y, area.w, h, 6, t.panel, if (s.done) t.gold else t.line)
            if (c.hovered(area.x, y, area.w, HEAD)) c.panel(area.x, y, area.w, HEAD, 6, t.panel2)
            c.stack(s.icon.stack(), area.x + 4, y + (HEAD - 16) / 2)
            val nameX = area.x + 25
            val textY = y + (HEAD - 7) / 2
            c.bold(s.name, nameX, textY, if (s.done) t.gold else t.fg, PvCtx.S_MD)
            // Right cluster: [label] [bar] [chevron]; bar drops out when the row is too narrow.
            val nameEnd = nameX + c.textW(s.name, PvCtx.S_MD) + 10
            val lw = c.textW(s.label, PvCtx.S_SM)
            var rx = area.right - 22
            if (rx - BAR_W - 8 - lw >= nameEnd) {
                rx -= BAR_W
                c.bar(rx, y + HEAD / 2 - 2, BAR_W, 4, s.frac, if (s.done) t.gold else t.acc)
                rx -= 8
            }
            if (rx - lw >= nameEnd) c.text(s.label, rx - lw, textY + 1, if (s.done) t.gold else t.mut, PvCtx.S_SM)
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
        val tw = (w - 18 - TGAP * (per - 1)) / per
        for ((i, tl) in s.tiles.withIndex()) {
            val tx = x + 9 + (i % per) * (tw + TGAP)
            val ty = y + (i / per) * (TH + TGAP)
            // Dark card; maxed = gold border + gold name, never a gold fill.
            c.panel(tx, ty, tw, TH, 5, t.panel2, if (tl.maxed) t.gold else t.line)
            c.stack(tl.icon.stack(), tx + 5, ty + (TH - 16) / 2)
            val tx2 = tx + 26
            val inner = tw - 26 - 6
            val tierW = c.textW(tl.tierTxt, PvCtx.S_SM)
            val nameMax = inner - tierW - 5
            if (tl.shownW != nameMax) {
                var nm = tl.name
                while (nm.length > 3 && c.textW(nm, PvCtx.S_SM) > nameMax) nm = nm.dropLast(2) + "…"
                tl.shown = nm; tl.shownW = nameMax
            }
            c.bold(tl.shown, tx2, ty + 5, if (tl.maxed) t.gold else t.fg, PvCtx.S_SM)
            c.text(tl.tierTxt, tx2 + inner - tierW, ty + 5, if (tl.maxed) t.gold else t.mut, PvCtx.S_SM)
            c.legacy(tl.killsTxt, tx2, ty + 15, PvCtx.S_XS, t.mut)
            c.bar(tx2, ty + TH - 6, inner, 2, tl.frac, if (tl.maxed) t.gold else t.acc)
            c.tip(tx, ty, tw, TH, tl.tip)
        }
    }
}
