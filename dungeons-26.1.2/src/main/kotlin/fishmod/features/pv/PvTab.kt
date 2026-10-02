package fishmod.features.pv

import fishmod.features.ScreenTheme
import fishmod.features.pv.tabs.*
import fishmod.utils.rendering.UiRecorder
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
    }

    fun text(s: String, x: Int, y: Int, color: Int = theme.fg, size: Float = S_MD) = UiRecorder.text(s, x.toFloat(), y.toFloat(), size, color)
    fun bold(s: String, x: Int, y: Int, color: Int = theme.fg, size: Float = S_MD) = UiRecorder.textBold(s, x.toFloat(), y.toFloat(), size, color)
    // Text with § colour codes; returns width.
    fun legacy(s: String, x: Int, y: Int, size: Float = S_MD, base: Int = theme.fg): Int {
        ScreenTheme.nLegacyText(s, x, y, base, size); return textW(strip(s), size)
    }
    fun textW(s: String, size: Float = S_MD): Int = Math.ceil(UiRecorder.textWidth(strip(s), size).toDouble()).toInt()
    fun strip(s: String) = s.replace(STRIP, "")

    // Overlay shapes (never under an item).
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int, r: Float = 0f) = UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, color)
    fun ring(x: Int, y: Int, w: Int, h: Int, r: Float, fill: Int, line: Int) = UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r, 1f, fill, line)
    fun bar(x: Int, y: Int, w: Int, h: Int, frac: Double, color: Int) {
        rect(x, y, w, h, theme.track, h / 2f)
        val fw = (w * frac.coerceIn(0.0, 1.0)).toInt()
        if (fw > 0) rect(x, y, fw.coerceAtLeast(h), h, color, h / 2f)
    }

    // Vanilla-layer panel: safe to put items on.
    fun panel(x: Int, y: Int, w: Int, h: Int, r: Int, fill: Int, border: Int = 0) {
        if (border != 0) { ScreenTheme.roundedRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, border) }
        ScreenTheme.roundedRect(g, x, y, w, h, r, fill)
    }
    // Card with optional heading; returns the y below the heading.
    fun card(x: Int, y: Int, w: Int, h: Int, title: String? = null): Int {
        panel(x, y, w, h, 8, theme.panel, theme.line)
        if (title == null) return y + 8
        bold(title, x + 9, y + 7, theme.fg, S_LG)
        return y + 21
    }

    fun hovered(x: Int, y: Int, w: Int, h: Int) = mouse.inView && mouse.x >= x && mouse.x < x + w && mouse.y >= y && mouse.y < y + h

    // Hover tooltip (opaque) for a region; lines use § codes.
    fun tip(x: Int, y: Int, w: Int, h: Int, lines: List<String>) { if (lines.isNotEmpty() && hovered(x, y, w, h)) screen.setTip(lines) }
    fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) = screen.addHit(x, y, w, h, action)

    // Item slot (vanilla layer) with rarity edge + tooltip.
    fun item(it: PvItem?, x: Int, y: Int, size: Int = SLOT) {
        ScreenTheme.roundedRect(g, x, y, size, size, 3, theme.slot)
        if (it == null) return
        val rc = PvTables.RARITY_COLOR[it.rarity]
        if (rc != null) g.fill(x + 2, y + size - 2, x + size - 2, y + size - 1, rc)
        val o = (size - 16) / 2
        g.item(it.stack, x + o, y + o)
        if (it.count > 1) g.itemDecorations(font, it.stack, x + o, y + o)
        tip(x, y, size, size, it.tooltip)
    }
    fun stack(st: ItemStack, x: Int, y: Int) = g.item(st, x, y)

    // Pill button; returns its width.
    fun pill(x: Int, y: Int, label: String, on: Boolean, size: Float = S_SM, h: Int = 13, action: (() -> Unit)? = null): Int {
        val w = textW(label, size) + 12
        val hov = action != null && hovered(x, y, w, h)
        ring(x, y, w, h, h / 2f, if (on) theme.acc else if (hov) theme.panel else theme.panel2, if (on) theme.acc else theme.line)
        legacy(label, x + 6, y + ((h - size) / 2f).toInt(), size, if (on) theme.accInk else if (hov) theme.fg else theme.fg)
        if (action != null) hit(x, y, w, h, action)
        return w
    }

    // Icon + name + level + bar row with hover XP detail.
    fun levelRow(x: Int, y: Int, w: Int, icon: ItemStack?, name: String, lvl: PvTables.Level, extra: List<String> = emptyList(), xpKnown: Boolean = true): Int {
        val h = 20
        val col = if (lvl.maxed) theme.gold else theme.acc
        ScreenTheme.roundedRect(g, x, y + 1, 18, 18, 5, if (lvl.maxed) theme.gold else theme.acc)
        if (icon != null) g.item(icon, x + 1, y + 2)
        val tx = x + 23
        bold(name, tx, y + 1, theme.fg, S_MD)
        val lvTxt = lvl.level.toString()
        text(lvTxt, tx + textW(name) + 5, y + 1, col, S_MD)
        val right = if (!xpKnown) "API off" else if (lvl.maxed) "MAX" else "${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())}"
        text(right, x + w - textW(right, S_XS), y + 2, theme.mut, S_XS)
        bar(tx, y + 12, w - (tx - x), 5, if (lvl.maxed) 1.0 else lvl.progress, col)
        val lines = ArrayList<String>()
        lines += "§f$name ${lvl.level}" + if (lvl.maxed) " §6MAX" else ""
        if (!xpKnown) lines += "§cSkill API disabled"
        else {
            lines += "§7Total XP: §f${full(lvl.totalXp.toDouble())}"
            if (lvl.maxed) { if (lvl.xpInto > 0) lines += "§7Overflow: §f${fmt(lvl.xpInto.toDouble())}" }
            else {
                lines += "§7Progress: §a${"%.1f".format(lvl.progress * 100)}% §7to ${lvl.level + 1}"
                lines += "§7Next level: §f${fmt(lvl.xpInto.toDouble())} / ${fmt(lvl.xpNeeded.toDouble())} XP"
            }
            lines += "§7Cap: §f${lvl.cap}"
        }
        if (extra.isNotEmpty()) { lines += ""; lines += extra }
        tip(x, y, w, h, lines)
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
