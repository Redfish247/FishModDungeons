package fishmod.features.pv

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

// In-game SkyBlock profile viewer (/pv).
class PvScreen(query: String?) : Screen(Component.literal("Profile Viewer")), HasUiOverlay {

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private var load = PvData.load(query)
    private var tabId = "home"
    private val subSel = HashMap<String, Int>()
    private val scrolls = HashMap<String, Int>()
    private var profileId: String? = null
    private var memberUuid: String? = null
    private var dropdown = 0 // 0 none, 1 co-op, 2 profile
    private var dropX = 0
    private var dropY = 0
    private var popupRect: PvRect? = null

    private val hits = ArrayList<Hit>()
    private val contentHits = ArrayList<Hit>()
    private val popupHits = ArrayList<Hit>()
    private var collectingContent = false
    private var tip: List<String>? = null
    private var tipSrc: List<String>? = null
    private var tipComps: List<Component> = emptyList()

    private var k = 1f
    private var dp = 0.5f
    private var vw = 0
    private var vh = 0
    private var view = PvRect(0, 0, 0, 0)
    private var contentH = 0
    private var lastCtx: PvCtx? = null

    private lateinit var nameField: EditBox
    private var fieldRect = PvRect(0, 0, 0, 0)

    private val theme get() = PvTheme.ALL[skin.coerceIn(0, PvTheme.ALL.size - 1)]

    override fun init() {
        val prev = if (::nameField.isInitialized) nameField.value else (load.query ?: "")
        nameField = EditBox(font, 0, 0, 100, 14, Component.literal("Username"))
        nameField.setMaxLength(16)
        nameField.setBordered(false)
        nameField.setValue(prev)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}
    override fun isPauseScreen() = false

    override fun paintUiOverlay() {
        try { UiRenderer.paint(width, height, k) } catch (e: Exception) { FishDiag.fail("PvScreen.1", "pv paint failed", e) }
    }

    fun setTip(lines: List<String>) { tip = lines }
    fun addHit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        val hit = Hit(x, y, w, h, action)
        if (collectingContent) contentHits.add(hit) else hits.add(hit)
    }

    private fun currentProfile(r: PvResult): PvProfile? =
        r.profiles.firstOrNull { it.id == profileId } ?: r.selected

    private fun currentMember(r: PvResult, p: PvProfile): PvMember? =
        p.members[memberUuid ?: r.uuid] ?: p.members[r.uuid] ?: p.members.values.firstOrNull()

    fun selectProfile(p: PvProfile) { profileId = p.id; memberUuid = null; PvData.ensureProfileExtras(p); dropdown = 0; scrolls.clear() }
    fun selectMember(uuid: String) { memberUuid = uuid; dropdown = 0; scrolls.clear() }
    fun selectTab(id: String) { tabId = id; dropdown = 0 }
    fun openProfileMenu(x: Int, y: Int) { dropdown = 2; dropX = x; dropY = y }
    fun lookup(name: String) {
        load = PvData.load(name); profileId = null; memberUuid = null; dropdown = 0; scrolls.clear(); tabId = "home"
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, rawMx: Int, rawMy: Int, delta: Float) {
        try { renderAll(ctx, rawMx, rawMy) } catch (t: Throwable) { FishDiag.fail("PvScreen.2", "pv render failed tab=$tabId", t) }
    }

    private fun renderAll(ctx: GuiGraphicsExtractor, rawMx: Int, rawMy: Int) {
        UiRecorder.clear()
        hits.clear(); contentHits.clear(); popupHits.clear(); tip = null
        k = UiScale.factor()
        vw = (width / k).toInt(); vh = (height / k).toInt()
        val mx = (rawMx / k).toInt(); val my = (rawMy / k).toInt()
        val t = theme
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.pose().pushMatrix()
        ctx.pose().scale(k, k)
        ctx.fill(0, 0, vw + 1, vh + 1, 0x55000000)

        dp = PvCtx.devPx()
        // Centered 8:5 panel like the template, ~85% of the screen.
        val fw = min(vw * 0.85, vh * 0.85 * 1.6).toInt().coerceAtLeast(320).coerceAtMost(vw - 16)
        val fh = (fw / 1.6).toInt().coerceAtMost(vh - 16)
        val fx = (vw - fw) / 2; val fy = (vh - fh) / 2
        PvCtx.smoothRect(ctx, fx - 3, fy - 1, fw + 6, fh + 6, 17f, 0x1A000000, dp)
        PvCtx.smoothRect(ctx, fx - 1, fy - 1, fw + 2, fh + 2, 15f, t.line, dp)
        PvCtx.smoothRect(ctx, fx, fy, fw, fh, 14f, t.frame, dp)

        val res = if (load.state == PvLoad.State.READY) load.result else null
        val prof = res?.let { currentProfile(it) }
        val mem = if (res != null && prof != null) currentMember(res, prof) else null
        val tab = PvTabs.byId(tabId) ?: PvTabs.all.first()

        val barH = drawTopBar(fx, fy, fw, mx, my, res, prof)
        UiRecorder.fillRect(fx.toFloat(), (fy + barH).toFloat(), fw.toFloat(), 1f, t.line)

        var bodyX = fx
        val bodyY = fy + barH + 1
        val bodyH = fh - barH - 1
        val subs = tab.subTabs
        if (subs.isNotEmpty()) {
            val railW = 96
            var sy = bodyY + 8
            val sel = subSel[tab.id] ?: 0
            for ((i, s) in subs.withIndex()) {
                val on = i == sel
                val hov = !on && mx in fx + 6 until fx + railW - 6 && my in sy until sy + 16
                if (on) {
                    UiRecorder.fillRoundedRect((fx + 6).toFloat(), sy.toFloat(), (railW - 12).toFloat(), 16f, 5f, t.panel)
                    UiRecorder.fillRect((fx + 6).toFloat(), (sy + 2).toFloat(), 2f, 12f, t.acc)
                }
                UiRecorder.text(s, (fx + 13).toFloat(), mid(sy, 16, PvCtx.S_SM), PvCtx.S_SM, if (on || hov) t.fg else t.mut)
                addHit(fx + 6, sy, railW - 12, 16) { subSel[tab.id] = i; scrolls[tab.id] = 0 }
                sy += 18
            }
            UiRecorder.fillRect((fx + railW).toFloat(), bodyY.toFloat(), 1f, bodyH.toFloat(), t.line)
            bodyX = fx + railW + 1
        }
        val bodyW = fx + fw - bodyX
        view = PvRect(bodyX + 2, bodyY + 2, bodyW - 4, bodyH - 8)
        val area0 = PvRect(bodyX + 12, bodyY + 10, bodyW - 26, bodyH - 20)

        if (res == null || prof == null || mem == null) {
            drawStatus(area0, mx, my, res)
        } else {
            PvData.ensureProfileExtras(prof)
            val overPopup = popupRect?.contains(mx, my) == true && dropdown != 0
            val mouse = PvMouse(mx, my, view.contains(mx, my) && !overPopup)
            val c = PvCtx(ctx, font, t, this, res, prof, mem, (subSel[tab.id] ?: 0).coerceIn(0, max(0, subs.size - 1)), mouse)
            lastCtx = c
            contentH = FishDiag.guard("PvScreen.3", "tab height failed ${tab.id}") { tab.height(c, area0) } ?: 0
            val scroll = (scrolls[tab.id] ?: 0).coerceIn(0, maxScroll())
            scrolls[tab.id] = scroll
            val area = PvRect(area0.x, area0.y - scroll, area0.w, area0.h)
            runCatching { ctx.enableScissor(view.x, view.y, view.right, view.bottom) }
            UiRecorder.pushScissor(view.x.toFloat(), view.y.toFloat(), view.w.toFloat(), view.h.toFloat())
            collectingContent = true
            FishDiag.guard("PvScreen.4", "tab render failed ${tab.id}") { tab.render(c, area, mouse) }
            collectingContent = false
            UiRecorder.popScissor()
            runCatching { ctx.disableScissor() }
            val ms = maxScroll()
            if (ms > 0) {
                val trackH = view.h
                val knobH = max(20, trackH * trackH / (trackH + ms))
                val knobY = view.y + (trackH - knobH) * scroll / ms
                UiRecorder.fillRoundedRect((view.right - 3).toFloat(), knobY.toFloat(), 3f, knobH.toFloat(), 1.5f, t.line)
            }
        }

        drawDropdown(res, prof, mx, my)
        ctx.pose().popMatrix()

        val tl = tip
        if (tl != null) {
            if (tl !== tipSrc) { tipSrc = tl; tipComps = tl.map { Component.literal(it) } }
            ScreenTheme.nItemTooltip(tipComps, mx, my, vw, vh, paintScale = k)
        }
    }

    private fun boldT(s: String, x: Float, y: Float, size: Float, col: Int) {
        UiRecorder.text(s, x, y, size, col); UiRecorder.text(s, x + dp, y, size, col)
    }
    private fun mid(y: Int, h: Int, size: Float) = PvCtx.midY(y.toFloat(), h.toFloat(), size)

    private fun maxScroll() = (contentH - (view.h - 10)).coerceAtLeast(0)

    private fun drawTopBar(fx: Int, fy: Int, fw: Int, mx: Int, my: Int, res: PvResult?, prof: PvProfile?): Int {
        val t = theme
        val y1 = fy + 8
        // Theme button
        val tl = t.name
        val tw = PvCtx.width(tl, PvCtx.S_SM).toInt() + 26
        val tx = fx + 12
        val hovT = mx in tx until tx + tw && my in y1 until y1 + 15
        UiRecorder.roundedRectRing(tx.toFloat(), y1.toFloat(), tw.toFloat(), 15f, 7.5f, 1f, if (hovT) t.panel else t.panel2, t.line)
        UiRecorder.disc(tx + 9f, y1 + 7.5f, 4f, t.acc)
        UiRecorder.disc(tx + 9f, y1 + 7.5f, 2f, t.gold)
        boldT(tl, (tx + 17).toFloat(), mid(y1, 15, PvCtx.S_SM), PvCtx.S_SM, t.fg)
        addHit(tx, y1, tw, 15) { skin = (skin + 1) % PvTheme.ALL.size }

        // Right cluster: profile, co-op, username field + View
        var rx = fx + fw - 12
        val vwBtn = 34
        rx -= vwBtn
        val hovV = mx in rx until rx + vwBtn && my in y1 until y1 + 15
        UiRecorder.fillRoundedRect(rx.toFloat(), y1.toFloat(), vwBtn.toFloat(), 15f, 7.5f, if (hovV) t.fg else t.acc)
        boldT("View", rx + 8f, mid(y1, 15, PvCtx.S_SM), PvCtx.S_SM, t.accInk)
        addHit(rx, y1, vwBtn, 15) { submitName() }
        val fieldW = 92
        rx -= fieldW + 2
        fieldRect = PvRect(rx, y1, fieldW, 15)
        UiRecorder.roundedRectRing(rx.toFloat(), y1.toFloat(), fieldW.toFloat(), 15f, 7.5f, 1f, t.panel2, if (nameField.isFocused) t.acc else t.line)
        ScreenTheme.nTextFieldContent(nameField, nameField.isFocused, rx + 5, y1, fieldW - 8, 15, PvCtx.S_SM)
        if (nameField.value.isEmpty() && !nameField.isFocused)
            UiRecorder.text("Username…", rx + 8f, mid(y1, 15, PvCtx.S_SM), PvCtx.S_SM, t.mut)
        if (prof != null) {
            val coop = "Co-op ${prof.members.size}"
            val cw = PvCtx.width(coop, PvCtx.S_SM).toInt() + 22
            rx -= cw + 6
            val bx = rx
            val hov = mx in bx until bx + cw && my in y1 until y1 + 15
            UiRecorder.roundedRectRing(bx.toFloat(), y1.toFloat(), cw.toFloat(), 15f, 7.5f, 1f, if (hov || dropdown == 1) t.panel else t.panel2, t.line)
            boldT(coop, bx + 7f, mid(y1, 15, PvCtx.S_SM), PvCtx.S_SM, t.fg)
            PvCtx.chevron(bx + cw - 10f, y1 + 7.5f, true, t.mut)
            addHit(bx, y1, cw, 15) { if (dropdown == 1) dropdown = 0 else { dropdown = 1; dropX = bx + cw; dropY = y1 + 18 } }
            val pl = prof.cuteName + (if (prof.modeIcon.isNotEmpty()) " " + prof.modeIcon else "")
            val pw = PvCtx.width(pl, PvCtx.S_SM).toInt() + 22
            rx -= pw + 6
            val px = rx
            val hovP = mx in px until px + pw && my in y1 until y1 + 15
            UiRecorder.roundedRectRing(px.toFloat(), y1.toFloat(), pw.toFloat(), 15f, 7.5f, 1f, if (hovP || dropdown == 2) t.panel else t.panel2, t.line)
            boldT(pl, px + 7f, mid(y1, 15, PvCtx.S_SM), PvCtx.S_SM, t.fg)
            PvCtx.chevron(px + pw - 10f, y1 + 7.5f, true, t.mut)
            addHit(px, y1, pw, 15) { if (dropdown == 2) dropdown = 0 else openProfileMenu(px + pw, y1 + 18) }
        }
        val nameLabel = res?.name ?: ""
        if (nameLabel.isNotEmpty() && tx + tw + 8 + PvCtx.width(nameLabel, PvCtx.S_MD) < rx - 6)
            boldT(nameLabel, tx + tw + 8f, mid(y1, 15, PvCtx.S_MD), PvCtx.S_MD, t.fg)

        // Tab strip, spread across the width
        val y2 = y1 + 20
        val tabs = PvTabs.all
        // Shrink font/padding until every tab fits on one row.
        val avail = (fw - 24).toFloat()
        var size = PvCtx.S_SM; var pad = 12f
        var textTotal = tabs.sumOf { PvCtx.width(it.title, size).toDouble() }.toFloat()
        while (textTotal + pad * tabs.size > avail && (size > 5f || pad > 6f)) {
            if (pad > 6f) pad -= 2f else size -= 0.25f
            textTotal = tabs.sumOf { PvCtx.width(it.title, size).toDouble() }.toFloat()
        }
        val gap = if (tabs.size > 1) ((avail - textTotal - pad * tabs.size) / (tabs.size - 1)).coerceAtLeast(0f) else 0f
        var x = (fx + 12).toFloat()
        for (tab in tabs) {
            val w = PvCtx.width(tab.title, size) + pad
            val on = tab.id == tabId
            val hov = !on && mx >= x && mx < x + w && my in y2 until y2 + 14
            if (on) UiRecorder.fillRoundedRect(x, y2.toFloat(), w, 14f, 7f, t.acc)
            val col = if (on) t.accInk else if (hov) t.fg else t.mut
            boldT(tab.title, x + pad / 2, mid(y2, 14, size), size, col)
            addHit(x.toInt(), y2, w.toInt() + 1, 14) { selectTab(tab.id) }
            x += w + gap
        }
        return y2 + 18 - fy
    }

    private fun drawStatus(a: PvRect, mx: Int, my: Int, res: PvResult?) {
        val t = theme
        val cx = a.x + a.w / 2
        val cy = a.y + a.h / 2 - 10
        val msg = when (load.state) {
            PvLoad.State.LOADING -> load.message
            PvLoad.State.ERROR -> load.message
            PvLoad.State.READY -> if (res?.profiles.isNullOrEmpty()) "No profiles" else "No member data on this profile"
        }
        if (load.state == PvLoad.State.LOADING) {
            val ph = (System.currentTimeMillis() / 120 % 8).toInt()
            for (i in 0 until 8) {
                val ang = i * Math.PI / 4
                UiRecorder.disc((cx + Math.cos(ang) * 10).toFloat(), (cy - 18 + Math.sin(ang) * 10).toFloat(), 2.2f,
                    if (i == ph) t.acc else t.track)
            }
        }
        val w = PvCtx.width(msg, PvCtx.S_LG)
        boldT(msg, cx - w / 2, cy.toFloat(), PvCtx.S_LG, if (load.state == PvLoad.State.ERROR) t.bad else t.fg)
        if (load.state == PvLoad.State.ERROR) {
            val bw = 50; val bx = cx - bw / 2; val by = cy + 16
            val hov = mx in bx until bx + bw && my in by until by + 15
            UiRecorder.fillRoundedRect(bx.toFloat(), by.toFloat(), bw.toFloat(), 15f, 7.5f, if (hov) t.fg else t.acc)
            boldT("Retry", bx + (bw - PvCtx.width("Retry", PvCtx.S_SM)) / 2f, mid(by, 15, PvCtx.S_SM), PvCtx.S_SM, t.accInk)
            addHit(bx, by, bw, 15) { lookup(load.query ?: (Minecraft.getInstance().user.name)) }
        }
    }

    private fun drawDropdown(res: PvResult?, prof: PvProfile?, mx: Int, my: Int) {
        popupRect = null
        if (dropdown == 0 || res == null || prof == null) { dropdown = 0; return }
        val t = theme
        val rows: List<Pair<String, () -> Unit>> = if (dropdown == 1) {
            prof.members.values.map { m ->
                val nm = m.name ?: PvData.nameFor(m.uuid)?.also { m.name = it } ?: m.uuid.take(8)
                val viewing = m.uuid == (memberUuid ?: res.uuid)
                val cata = m.dungeons?.cata?.level ?: 0
                "§f§l$nm" + (if (viewing) " §7· viewing" else "") + "  §7Lvl §b${m.sbLevel.level} §7· Cata §c$cata §7· §6${fmt(m.purse)}" to { selectMember(m.uuid) }
            }
        } else {
            res.profiles.map { p ->
                val mark = if (p.selected) " §a●" else ""
                val icon = if (p.modeIcon.isNotEmpty()) " §e${p.modeIcon}" else ""
                "§f§l${p.cuteName}$icon$mark  §7${p.members.size} member" + (if (p.members.size == 1) "" else "s") to { selectProfile(p) }
            }
        }
        val header = if (dropdown == 1) "Co-op members (${prof.members.size}) · ${prof.cuteName}" else "Profiles (${res.profiles.size})"
        val rowH = 16
        val w = max(PvCtx.width(header, PvCtx.S_SM).toInt(), rows.maxOfOrNull { PvCtx.width(it.first, PvCtx.S_SM).toInt() + 2 } ?: 0) + 24
        val h = 20 + rows.size * rowH + 4
        val x = (dropX - w).coerceAtLeast(4)
        val y = dropY
        popupRect = PvRect(x, y, w, h)
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 8f, 0x66000000)
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 1f, opaque(t.frame), t.line)
        UiRecorder.text(header, x + 10f, mid(y + 2, 16, PvCtx.S_SM), PvCtx.S_SM, t.mut)
        var ry = y + 18
        for ((label, action) in rows) {
            val hov = mx in x + 4 until x + w - 4 && my in ry until ry + rowH
            if (hov) UiRecorder.fillRoundedRect(x + 4f, ry.toFloat(), w - 8f, rowH.toFloat(), 5f, t.panel)
            lastCtx?.legacyF(label, x + 10f, mid(ry, rowH, PvCtx.S_SM), PvCtx.S_SM, t.fg) ?: UiRecorder.text(PvCtx.strip(label), x + 10f, mid(ry, rowH, PvCtx.S_SM), PvCtx.S_SM, t.fg)
            popupHits.add(Hit(x + 4, ry, w - 8, rowH, action))
            ry += rowH
        }
    }

    private fun opaque(c: Int): Int = if (skin == 1) 0xFFF4F7F2.toInt() else if (skin == 0) 0xF0141E19.toInt() else c or 0xFF000000.toInt()

    private fun submitName() {
        val n = nameField.value.trim()
        if (n.isNotEmpty()) { lookup(n); nameField.isFocused = false }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = (click.x() / k).toInt(); val my = (click.y() / k).toInt()
        if (dropdown != 0) {
            popupHits.firstOrNull { it.contains(mx, my) }?.let { it.action(); return true }
            dropdown = 0
            if (popupRect?.contains(mx, my) == true) return true
        }
        val inField = fieldRect.contains(mx, my)
        nameField.isFocused = inField
        if (inField) { nameField.mouseClicked(click, doubled); return true }
        hits.lastOrNull { it.contains(mx, my) }?.let { it.action(); return true }
        if (view.contains(mx, my)) {
            contentHits.lastOrNull { it.contains(mx, my) }?.let { it.action(); return true }
            val c = lastCtx
            val tab = PvTabs.byId(tabId)
            if (c != null && tab != null && FishDiag.guard("PvScreen.5", "tab click failed") { tab.onClick(c, mx, my, click.button()) } == true) return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, h: Double, v: Double): Boolean {
        val mx = (mouseX / k).toInt(); val my = (mouseY / k).toInt()
        val c = lastCtx
        val tab = PvTabs.byId(tabId) ?: return true
        if (c != null && FishDiag.guard("PvScreen.6", "tab scroll failed") { tab.onScroll(c, mx, my, v) } == true) return true
        scrolls[tabId] = ((scrolls[tabId] ?: 0) - (v * 24).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (dropdown != 0) { dropdown = 0; return true }
            if (nameField.isFocused) { nameField.isFocused = false; return true }
            onClose(); return true
        }
        if (nameField.isFocused) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { submitName(); return true }
            nameField.keyPressed(input); return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (nameField.isFocused && nameField.charTyped(input)) return true
        return super.charTyped(input)
    }

    companion object {
        var skin = 0

        @JvmStatic
        fun open(name: String?) {
            val mc = Minecraft.getInstance()
            mc.schedule { mc.setScreen(PvScreen(name)) }
        }
    }
}
