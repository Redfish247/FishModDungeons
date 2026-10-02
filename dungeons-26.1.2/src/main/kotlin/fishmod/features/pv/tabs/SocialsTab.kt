package fishmod.features.pv.tabs

import fishmod.features.pv.*
import net.minecraft.client.Minecraft
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

object SocialsTab : PvTab {
    override val id = "social"
    override val title = "Socials"

    private const val GAP = 10
    private const val ROW = 12
    private val DATE = SimpleDateFormat("MMM d, yyyy")
    private val COLORS = mapOf(
        "BLACK" to "0", "DARK_BLUE" to "1", "DARK_GREEN" to "2", "DARK_AQUA" to "3", "DARK_RED" to "4", "DARK_PURPLE" to "5",
        "GOLD" to "6", "GRAY" to "7", "DARK_GRAY" to "8", "BLUE" to "9", "GREEN" to "a", "AQUA" to "b", "RED" to "c",
        "LIGHT_PURPLE" to "d", "YELLOW" to "e", "WHITE" to "f",
    )
    private val GUILD_XP = longArrayOf(100_000, 150_000, 250_000, 500_000, 750_000, 1_000_000, 1_250_000, 1_500_000, 2_000_000,
        2_500_000, 2_500_000, 2_500_000, 2_500_000, 2_500_000, 3_000_000)
    private val PLATFORMS = listOf("DISCORD" to "§9", "HYPIXEL" to "§6", "YOUTUBE" to "§c", "TWITCH" to "§5", "TWITTER" to "§b", "INSTAGRAM" to "§d", "TIKTOK" to "§f")

    private var copied: String? = null
    private var copiedAt = 0L

    private fun hypixelRows(c: PvCtx): List<Pair<String, String>> {
        val p = c.result.player ?: return listOf("" to when (c.result.playerStatus) { "loading" -> "§7Loading…"; else -> "§cPlayer data unavailable" })
        return listOfNotNull(
            "Rank" to (p.rankPrefix.takeIf { it.length > 2 } ?: "§7None"),
            p.networkLevel?.let { "Network Level" to "§a${"%.2f".format(it)}" },
            p.karma?.let { "Karma" to "§d${full(it.toDouble())}" },
            p.achievementPoints?.let { "Achievement Points" to "§e${full(it.toDouble())}" },
            p.firstLogin?.let { "First Login" to DATE.format(Date(it)) },
            p.lastLogin?.let { "Last Login" to DATE.format(Date(it)) },
        )
    }

    private fun guildLevel(xp: Long): Double {
        var left = xp; var lvl = 0
        while (true) {
            val need = GUILD_XP.getOrElse(lvl) { 3_000_000 }
            if (left < need) return lvl + left.toDouble() / need
            left -= need; lvl++
        }
    }

    private fun guildRows(c: PvCtx): List<Pair<String, String>> {
        val g = c.result.guild ?: return listOf("" to when (c.result.guildStatus) { "loading" -> "§7Loading…"; "none" -> "§7Not in a guild"; else -> "§cGuild unavailable" })
        val col = "§" + (COLORS[g.raw.str("tagColor") ?: "GRAY"] ?: "7")
        val me = g.raw.arr("members")?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }?.firstOrNull { it.str("uuid") == c.result.uuid.replace("-", "") }
        return listOfNotNull(
            "Name" to "§f${g.name}" + (g.tag?.let { " $col[$it]" } ?: ""),
            g.raw.long("exp")?.let { "Level" to "§a${"%.2f".format(guildLevel(it))}" },
            "Members" to "${g.memberCount}",
            me?.str("rank")?.let { "Rank" to it },
            me?.long("joined")?.let { "Joined" to DATE.format(Date(it)) },
        )
    }

    private fun links(c: PvCtx) = c.result.player?.socials.orEmpty().entries.sortedBy { e -> PLATFORMS.indexOfFirst { it.first == e.key }.let { if (it < 0) 99 else it } }

    private fun cardH(n: Int) = 21 + max(1, n) * ROW + 6

    override fun height(c: PvCtx, area: PvRect): Int {
        val hs = listOf(cardH(hypixelRows(c).size), cardH(guildRows(c).size), cardH(links(c).size))
        return if (area.w < 480) hs.sum() + 2 * GAP else hs.max()
    }

    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val narrow = area.w < 480
        val cw = if (narrow) area.w else (area.w - 2 * GAP) / 3
        var x = area.x; var y = area.y
        fun next(h: Int) { if (narrow) y += h + GAP else x += cw + GAP }
        val hr = hypixelRows(c); kvCard(c, x, y, cw, "Hypixel", hr); next(cardH(hr.size))
        val gr = guildRows(c); kvCard(c, x, y, cw, "Guild", gr); next(cardH(gr.size))
        val ls = links(c)
        var ly = c.card(x, y, cw, cardH(ls.size), "Links")
        if (ls.isEmpty()) c.text(if (c.result.player == null) "Unavailable" else "No linked socials", x + 9, ly, c.theme.mut, PvCtx.S_SM)
        for ((k, v) in ls) {
            val pc = PLATFORMS.firstOrNull { it.first == k }?.second ?: "§7"
            val lw = c.legacy("$pc${PvData.pretty(k)}", x + 9, ly, PvCtx.S_SM)
            val shown = if (copied == k && System.currentTimeMillis() - copiedAt < 1500) "§aCopied!" else "§f$v"
            c.legacy(clip(c, shown, cw - lw - 26), x + 9 + lw + 8, ly, PvCtx.S_SM)
            c.tip(x + 4, ly - 1, cw - 8, ROW, listOf("$pc${PvData.pretty(k)}", "§f$v", "§8Click to copy"))
            c.hit(x + 4, ly - 1, cw - 8, ROW) { Minecraft.getInstance().keyboardHandler.setClipboard(v); copied = k; copiedAt = System.currentTimeMillis() }
            ly += ROW
        }
    }

    private fun kvCard(c: PvCtx, x: Int, y: Int, w: Int, title: String, rows: List<Pair<String, String>>) {
        var ry = c.card(x, y, w, cardH(rows.size), title)
        for ((k, v) in rows) {
            if (k.isEmpty()) { c.legacy(v, x + 9, ry, PvCtx.S_SM); ry += ROW; continue }
            c.text(k, x + 9, ry, c.theme.mut, PvCtx.S_SM)
            val vw = c.textW(v, PvCtx.S_SM); c.legacy(v, x + w - 9 - vw, ry, PvCtx.S_SM)
            ry += ROW
        }
    }

    private fun clip(c: PvCtx, s: String, w: Int): String {
        if (c.textW(s, PvCtx.S_SM) <= w) return s
        var out = s
        while (out.length > 4 && c.textW("$out…", PvCtx.S_SM) > w) out = out.dropLast(1)
        return "$out…"
    }
}
