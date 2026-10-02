package fishmod.features.pv

import fishmod.features.pv.tabs.*
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

// One tab of the profile viewer. Each lives in its own file under pv/tabs.
interface PvTab {
    val id: String
    val title: String
    val subTabs: List<String> get() = emptyList()
    // Total content height for the given area (drives scrolling).
    fun height(c: PvCtx, area: PvRect): Int
    // Draw at area (area.y already includes scroll offset).
    fun render(c: PvCtx, area: PvRect, mouse: PvMouse)
    fun onClick(c: PvCtx, mx: Int, my: Int, button: Int): Boolean = false
    fun onScroll(c: PvCtx, mx: Int, my: Int, amount: Double): Boolean = false
}

// SkyCrypt order; replace a placeholder file to implement a tab.
object PvTabs {
    val all: List<PvTab> = listOf(
        HomeTab, AccessoriesTab, PetsTab, InventoryTab, SkillsTab, DungeonsTab, SlayerTab, MinionsTab,
        BestiaryTab, CollectionsTab, CrimsonIsleTab, RiftTab, MiscTab, MuseumTab, SocialsTab,
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

data class PvRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right get() = x + w
    val bottom get() = y + h
    fun contains(px: Int, py: Int) = px >= x && px < x + w && py >= y && py < y + h
}

class PvMouse(val x: Int, val y: Int, val inView: Boolean)

class PvTheme(
    val name: String, val frame: Int, val panel: Int, val panel2: Int, val line: Int, val fg: Int, val mut: Int,
    val acc: Int, val gold: Int, val track: Int, val bad: Int, val accInk: Int, val slot: Int,
) {
    companion object {
        val ALL = listOf(
            PvTheme("Liquid Glass", 0x8C0E1A14.toInt(), 0x22FFFFFF, 0x14FFFFFF, 0x38FFFFFF, 0xFFFFFFFF.toInt(), 0xB8ECF4EE.toInt(),
                0xFF3BE37A.toInt(), 0xFFFFC23D.toInt(), 0x29FFFFFF, 0xFFFF8A80.toInt(), 0xFF06301A.toInt(), 0x1AFFFFFF),
            PvTheme("Light", 0xF2F4F7F2.toInt(), 0xFFFFFFFF.toInt(), 0x0F142814, 0x26142814, 0xFF172017.toInt(), 0xFF56645A.toInt(),
                0xFF13A54A.toInt(), 0xFFD99A0B.toInt(), 0x1F142814, 0xFFD0453C.toInt(), 0xFFFFFFFF.toInt(), 0x14142814),
            PvTheme("Gray", 0xF03E4045.toInt(), 0xFF4A4D53.toInt(), 0x0DFFFFFF, 0x1FFFFFFF, 0xFFEEEFF1.toInt(), 0xFFB4B8BF.toInt(),
                0xFF4CD17E.toInt(), 0xFFF2B93B.toInt(), 0x47000000, 0xFFFF8A80.toInt(), 0xFF0B2A17.toInt(), 0x24000000),
            PvTheme("Black", 0xED08090A.toInt(), 0xFF121315.toInt(), 0x0AFFFFFF, 0x17FFFFFF, 0xFFF3F4F5.toInt(), 0xFF8F949B.toInt(),
                0xFF2FD16A.toInt(), 0xFFF0B232.toInt(), 0xFF1F2124.toInt(), 0xFFFF6A5F.toInt(), 0xFF04230F.toInt(), 0xFF1A1B1E.toInt()),
        )
    }
}

// Per-frame drawing context handed to tabs. Backgrounds under items go to the vanilla layer; text/bars to the overlay.
class PvCtx(
    val g: GuiGraphicsExtractor, val font: Font, val theme: PvTheme, val screen: PvScreen,
    val result: PvResult, val profile: PvProfile, val member: PvMember, val sub: Int, val mouse: PvMouse,
) {
    companion object {
        const val S_XS = 6f
        const val S_SM = 6.5f
        const val S_MD = 7.5f
        const val S_LG = 9f
        const val S_XL = 12f
        const val SLOT = 18

        // One device pixel in virtual (recorded) units.
        fun devPx(): Float = 1f / (Minecraft.getInstance().window.guiScale * UiScale.factor()).toFloat().coerceAtLeast(0.01f)

        private val widths = HashMap<Float, HashMap<String, Float>>()
        private val stripped = HashMap<String, String>()
        private val runs = HashMap<String, Array<Run>>()
        private class Run(val text: String, val color: Int, val bold: Boolean) // color -1 = base

        fun width(s: String, size: Float): Float {
            val plain = strip(s)
            val m = widths.getOrPut(size) { HashMap() }
            if (m.size > 4096) m.clear()
            return m.getOrPut(plain) { UiRecorder.textWidth(plain, size) }
        }
        fun strip(s: String): String {
            if (s.indexOf('§') < 0) return s
            if (stripped.size > 4096) stripped.clear()
            return stripped.getOrPut(s) { s.replace(STRIP, "") }
        }

        private val CODES = intArrayOf(
            0xFF000000.toInt(), 0xFF0000AA.toInt(), 0xFF00AA00.toInt(), 0xFF00AAAA.toInt(), 0xFFAA0000.toInt(), 0xFFAA00AA.toInt(),
            0xFFFFAA00.toInt(), 0xFFAAAAAA.toInt(), 0xFF555555.toInt(), 0xFF5555FF.toInt(), 0xFF55FF55.toInt(), 0xFF55FFFF.toInt(),
            0xFFFF5555.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFFFFF.toInt(),
        )
        private fun parse(s: String): Array<Run> {
            runs[s]?.let { return it }
            val out = ArrayList<Run>()
            val sb = StringBuilder()
            var col = -1; var bold = false
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch == '§' && i + 1 < s.length) {
                    val c = s[i + 1].lowercaseChar()
                    val hex = "0123456789abcdef".indexOf(c)
                    val nc = if (hex >= 0) CODES[hex] else if (c == 'r') -1 else col
                    val nb = if (c == 'l') true else if (hex >= 0 || c == 'r') false else bold
                    if (nc != col || nb != bold) {
                        if (sb.isNotEmpty()) { out += Run(sb.toString(), col, bold); sb.setLength(0) }
                        col = nc; bold = nb
                    }
                    i += 2; continue
                }
                sb.append(ch); i++
            }
            if (sb.isNotEmpty()) out += Run(sb.toString(), col, bold)
            if (runs.size > 4096) runs.clear()
            return out.toTypedArray().also { runs[s] = it }
        }

        // Rounded rect on the vanilla layer drawn at device resolution (smooth corners at any GUI scale).
        fun smoothRect(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Float, color: Int, dp: Float = devPx()) {
            if (w <= 0 || h <= 0) return
            val s = 1f / dp
            val x0 = Math.round(x * s); val y0 = Math.round(y * s)
            val ww = Math.round((x + w) * s) - x0; val hh = Math.round((y + h) * s) - y0
            val rr = Math.min(Math.round(r * s), Math.min(ww, hh) / 2)
            val pose = g.pose()
            pose.pushMatrix()
            pose.scale(dp, dp)
            if (rr <= 0) g.fill(x0, y0, x0 + ww, y0 + hh, color)
            else {
                g.fill(x0, y0 + rr, x0 + ww, y0 + hh - rr, color)
                val a = (color ushr 24) and 0xFF
                for (i in 0 until rr) {
                    val dy = rr - i - 0.5
                    val dx = Math.sqrt(Math.max(0.0, rr.toDouble() * rr - dy * dy))
                    val fl = Math.floor(dx).toInt()
                    val inset = rr - fl
                    val top = y0 + i; val bot = y0 + hh - 1 - i
                    g.fill(x0 + inset, top, x0 + ww - inset, top + 1, color)
                    g.fill(x0 + inset, bot, x0 + ww - inset, bot + 1, color)
                    val cov = dx - fl
                    if (inset > 0 && cov > 0.04) {
                        val aa = ((a * cov).toInt() shl 24) or (color and 0xFFFFFF)
                        g.fill(x0 + inset - 1, top, x0 + inset, top + 1, aa)
                        g.fill(x0 + ww - inset, top, x0 + ww - inset + 1, top + 1, aa)
                        g.fill(x0 + inset - 1, bot, x0 + inset, bot + 1, aa)
                        g.fill(x0 + ww - inset, bot, x0 + ww - inset + 1, bot + 1, aa)
                    }
                }
            }
            pose.popMatrix()
        }

        // Smooth chevron (anti-aliased lines) centred on (cx, cy).
        fun chevron(cx: Float, cy: Float, open: Boolean, color: Int, size: Float = 3f) {
            if (open) {
                UiRecorder.line(cx - size, cy - size / 2, cx, cy + size / 2, 1.2f, color)
                UiRecorder.line(cx, cy + size / 2, cx + size, cy - size / 2, 1.2f, color)
            } else {
                UiRecorder.line(cx - size / 2, cy - size, cx + size / 2, cy, 1.2f, color)
                UiRecorder.line(cx + size / 2, cy, cx - size / 2, cy + size, 1.2f, color)
            }
        }

        // Y to draw text of `size` so it sits visually centred in a box (Inter: cap centre ~0.53 size below top).
        fun midY(boxY: Float, h: Float, size: Float): Float = boxY + h / 2f - size * 0.53f
    }

    val dp = devPx()

    fun textF(s: String, x: Float, y: Float, color: Int = theme.fg, size: Float = S_MD) = UiRecorder.text(s, x, y, size, color)
    // Faux bold: two passes 1 device px apart so the advance equals textW (UiRecorder bold widens every glyph).
    fun boldF(s: String, x: Float, y: Float, color: Int = theme.fg, size: Float = S_MD) {
        UiRecorder.text(s, x, y, size, color); UiRecorder.text(s, x + dp, y, size, color)
    }
    fun text(s: String, x: Int, y: Int, color: Int = theme.fg, size: Float = S_MD) = textF(s, x.toFloat(), y.toFloat(), color, size)
    fun bold(s: String, x: Int, y: Int, color: Int = theme.fg, size: Float = S_MD) = boldF(s, x.toFloat(), y.toFloat(), color, size)
    // Text with § colour codes; returns width.
    fun legacy(s: String, x: Int, y: Int, size: Float = S_MD, base: Int = theme.fg): Int = Math.ceil(legacyF(s, x.toFloat(), y.toFloat(), size, base).toDouble()).toInt()
    fun legacyF(s: String, x: Float, y: Float, size: Float = S_MD, base: Int = theme.fg): Float {
        var px = x
        for (r in parse(s)) {
            val col = if (r.color == -1) base else (r.color and 0xFFFFFF) or (base and 0xFF000000.toInt())
            if (r.bold) boldF(r.text, px, y, col, size) else textF(r.text, px, y, col, size)
            px += width(r.text, size)
        }
        return px - x
    }
    fun textW(s: String, size: Float = S_MD): Int = Math.ceil(width(s, size).toDouble()).toInt()
    fun strip(s: String) = PvCtx.strip(s)
    fun midY(boxY: Int, h: Int, size: Float): Float = PvCtx.midY(boxY.toFloat(), h.toFloat(), size)

    // Overlay shapes (never under an item).
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int, r: Float = 0f) = UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, color)
    fun ring(x: Int, y: Int, w: Int, h: Int, r: Float, fill: Int, line: Int) = UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, 1f, fill, line)
    fun bar(x: Int, y: Int, w: Int, h: Int, frac: Double, color: Int) {
        rect(x, y, w, h, theme.track, h / 2f)
        val fw = (w * frac.coerceIn(0.0, 1.0)).toInt()
        if (fw > 0) rect(x, y, fw.coerceAtLeast(h), h, color, h / 2f)
    }
    fun chevron(cx: Float, cy: Float, open: Boolean, color: Int = theme.mut) = PvCtx.chevron(cx, cy, open, color)

    // Vanilla-layer panel: safe to put items on.
    fun panel(x: Int, y: Int, w: Int, h: Int, r: Int, fill: Int, border: Int = 0) {
        if (border != 0) smoothRect(g, x - 1, y - 1, w + 2, h + 2, r + 1f, border, dp)
        smoothRect(g, x, y, w, h, r.toFloat(), fill, dp)
    }
    // Card with optional heading (+ muted suffix); returns the y below the heading.
    fun card(x: Int, y: Int, w: Int, h: Int, title: String? = null, suffix: String? = null): Int {
        panel(x, y, w, h, 8, theme.panel, theme.line)
        if (title == null) return y + 8
        val tw = legacyF("§l$title", x + 9f, y + 7f, S_LG, theme.fg)
        if (suffix != null) legacyF(suffix, x + 9f + tw + 6f, y + 7f + (S_LG - S_SM) * 0.53f, S_SM, theme.mut)
        return y + 21
    }

    fun hovered(x: Int, y: Int, w: Int, h: Int) = mouse.inView && mouse.x >= x && mouse.x < x + w && mouse.y >= y && mouse.y < y + h

    // Hover tooltip (opaque) for a region; lines use § codes.
    fun tip(x: Int, y: Int, w: Int, h: Int, lines: List<String>) { if (lines.isNotEmpty() && hovered(x, y, w, h)) screen.setTip(lines) }
    // Lazy variant: lines are only built while hovered.
    inline fun tipL(x: Int, y: Int, w: Int, h: Int, lines: () -> List<String>) { if (hovered(x, y, w, h)) lines().let { if (it.isNotEmpty()) screen.setTip(it) } }
    fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) = screen.addHit(x, y, w, h, action)

    // Item slot (vanilla layer) with rarity edge + tooltip.
    fun item(it: PvItem?, x: Int, y: Int, size: Int = SLOT) {
        smoothRect(g, x, y, size, size, 3f, theme.slot, dp)
        if (it == null) return
        val o = (size - 16) / 2
        g.item(it.stack, x + o, y + o)
        if (it.count > 1) g.itemDecorations(font, it.stack, x + o, y + o)
        if (hovered(x, y, size, size)) screen.setTip(it.tooltip)
    }
    fun stack(st: ItemStack, x: Int, y: Int) = g.item(st, x, y)

    // Pill button; returns its width.
    fun pill(x: Int, y: Int, label: String, on: Boolean, size: Float = S_SM, h: Int = 13, action: (() -> Unit)? = null): Int {
        val w = textW(label, size) + 12
        val hov = action != null && hovered(x, y, w, h)
        ring(x, y, w, h, h / 2f, if (on) theme.acc else if (hov) theme.panel else theme.panel2, if (on) theme.acc else theme.line)
        legacyF(label, x + 6f, midY(y, h, size), size, if (on) theme.accInk else theme.fg)
        if (action != null) hit(x, y, w, h, action)
        return w
    }

    // Icon + name + level + bar row with hover XP detail. Overflow levels go past the cap with a gold bar.
    fun levelRow(x: Int, y: Int, w: Int, icon: ItemStack?, name: String, lvl: PvTables.Level, extra: List<String> = emptyList(), xpKnown: Boolean = true): Int {
        val h = 20
        val col = if (lvl.maxed) theme.gold else theme.acc
        smoothRect(g, x, y + 1, 18, 18, 5f, col, dp)
        if (icon != null) g.item(icon, x + 1, y + 2)
        val tx = x + 23
        boldF(name, tx.toFloat(), y + 1f, theme.fg, S_MD)
        textF(lvl.level.toString(), tx + width(name, S_MD) + 4f, y + 1f, col, S_MD)
        val right = when {
            !xpKnown -> "API off"
            lvl.overflow -> "+${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())}"
            lvl.maxed -> "MAX"
            else -> "${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())}"
        }
        textF(right, x + w - width(right, S_XS), y + 1f + (S_MD - S_XS) * 0.53f, if (lvl.maxed) theme.gold else theme.mut, S_XS)
        bar(tx, y + 12, w - (tx - x), 5, if (lvl.maxed && !lvl.overflow) 1.0 else lvl.progress, col)
        tipL(x, y, w, h) {
            val lines = ArrayList<String>()
            lines += "§f$name ${lvl.level}" + when {
                lvl.overflow && lvl.level > lvl.cap -> " §6(cap ${lvl.cap})"
                lvl.maxed -> " §6MAX"
                else -> ""
            }
            if (!xpKnown) lines += "§cSkill API disabled"
            else {
                lines += "§7Total XP: §f${full(lvl.totalXp.toDouble())}"
                if (lvl.overflow) {
                    lines += "§7Overflow XP: §6${full(lvl.overflowXp.toDouble())}"
                    lines += "§7Next overflow level: §f${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())}"
                } else if (lvl.maxed) { if (lvl.xpInto > 0) lines += "§7Overflow: §f${fmt(lvl.xpInto.toDouble())}" }
                else {
                    lines += "§7Progress: §a${"%.1f".format(lvl.progress * 100)}% §7to ${lvl.level + 1}"
                    lines += "§7Next level: §f${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())} XP"
                }
                lines += "§7Cap: §f${lvl.cap}"
            }
            if (extra.isNotEmpty()) { lines += ""; lines += extra }
            lines
        }
        return h
    }
}

private val STRIP = Regex("§.")

fun fmt(v: Double?): String {
    if (v == null) return "?"
    val a = Math.abs(v)
    return when {
        a >= 1e12 -> "%.2fT".format(v / 1e12)
        a >= 1e9 -> "%.2fB".format(v / 1e9)
        a >= 1e6 -> "%.1fM".format(v / 1e6)
        a >= 1e3 -> "%.1fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }
}

fun full(v: Double?): String = if (v == null) "?" else "%,d".format(v.toLong())
