package fishmod.features.pv.tabs

import fishmod.features.pv.*

// Base for not-yet-built tabs: shows "Coming soon".
abstract class PlaceholderTab(override val id: String, override val title: String, override val subTabs: List<String> = emptyList()) : PvTab {
    override fun height(c: PvCtx, area: PvRect) = area.h
    override fun render(c: PvCtx, area: PvRect, mouse: PvMouse) {
        val head = if (subTabs.isEmpty()) title else "$title · ${subTabs.getOrElse(c.sub) { "" }}"
        val w = c.textW(head, PvCtx.S_XL)
        c.bold(head, area.x + (area.w - w) / 2, area.y + area.h / 2 - 14, c.theme.fg, PvCtx.S_XL)
        val s = "Coming soon"
        c.text(s, area.x + (area.w - c.textW(s)) / 2, area.y + area.h / 2 + 2, c.theme.mut)
    }
}
