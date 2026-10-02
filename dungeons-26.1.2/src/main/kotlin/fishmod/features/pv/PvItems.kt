package fishmod.features.pv

import fishmod.features.croesus.LootIcons
import fishmod.utils.HypixelApi
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile

// One API item: parsed off-thread, ItemStack built lazily on the render thread.
class PvItem(val tag: CompoundTag) {
    val extra: CompoundTag = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes")
    val id: String? = extra.getStringOr("id", "").ifEmpty { null }
    val uuid: String? = extra.getStringOr("uuid", "").ifEmpty { null }
    val count: Int = tag.getByteOr("Count", 1.toByte()).toInt().coerceAtLeast(1)
    private val display = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("display")
    val name: String = display.getStringOr("Name", id ?: "Unknown")
    val lore: List<String> = display.getListOrEmpty("Lore").let { l -> (0 until l.size).map { l.getString(it).orElse("") } }
    val rarity: String? = PvTables.rarityFromLore(lore)
    val recombobulated: Boolean = extra.getIntOr("rarity_upgrades", 0) > 0
    val stars: Int = extra.getIntOr("upgrade_level", extra.getIntOr("dungeon_item_level", 0))
    val enrichment: String? = extra.getStringOr("talisman_enrichment", "").ifEmpty { null }
    private val skullTexture: String? = tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("SkullOwner")
        .getCompoundOrEmpty("Properties").getListOrEmpty("textures").let { if (it.isEmpty) null else it.getCompoundOrEmpty(0).getStringOr("Value", "").ifEmpty { null } }
    private val dye: Int? = if (display.contains("color")) display.getIntOr("color", 0) else null
    private val glint = tag.getCompoundOrEmpty("tag").contains("ench")

    val tooltip: List<String> by lazy { listOf(name) + lore }

    val stack: ItemStack by lazy { FishDiag.guard("PvItem.1", "item build failed for $id") { build() } ?: ItemStack(Items.BARRIER) }

    private fun build(): ItemStack {
        val base = LootIcons.icon(id)?.copy()
            ?: if (skullTexture != null) ItemStack(Items.PLAYER_HEAD) else ItemStack(Items.PAPER)
        if (skullTexture != null && base.item == Items.PLAYER_HEAD) {
            val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", skullTexture))
            val profile = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(skullTexture.toByteArray()), "fmpv", com.mojang.authlib.properties.PropertyMap(props))
            base.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        }
        if (dye != null) base.set(DataComponents.DYED_COLOR, DyedItemColor(dye))
        if (glint) base.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        base.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        base.count = count.coerceAtMost(base.maxStackSize.coerceAtLeast(1))
        return base
    }

    companion object {
        fun decode(b64: String?): List<PvItem?> {
            if (b64.isNullOrEmpty()) return emptyList()
            return HypixelApi.decodeItems(b64).map { t -> t?.let { PvItem(it) } }
        }
    }
}

// A decoded inventory section.
class PvInv(val items: List<PvItem?>) {
    val size get() = items.size
    val isEmpty get() = items.all { it == null }
    companion object { val EMPTY = PvInv(emptyList()) }
}

// Pet icon + tooltip shared by tabs.
object PvPets {
    private val cache = HashMap<String, ItemStack>()
    fun icon(p: PvPet): ItemStack {
        val key = p.type + p.tier
        cache[key]?.let { return it }
        val idx = PvData.RARITY_ORDER.indexOf(p.tier).coerceAtLeast(0)
        val st = fishmod.features.PetIcons.icon(p.name) ?: LootIcons.icon("${p.type};$idx") ?: return ItemStack(Items.BONE)
        cache[key] = st
        return st
    }
    private val tips = java.util.WeakHashMap<PvPet, List<String>>()
    fun tooltip(p: PvPet): List<String> = tips.getOrPut(p) { buildTooltip(p) }
    private fun buildTooltip(p: PvPet): List<String> {
        val out = ArrayList<String>()
        out += "§7[Lvl ${p.level.level}] ${p.rarityCode}${p.name}"
        out += "§8${PvData.pretty(p.tier)} pet" + if (p.active) " §a· Active" else ""
        out += ""
        if (p.level.maxed) out += "§bMAX LEVEL" else {
            out += "§7Progress to ${p.level.level + 1}: §e${"%.1f".format(p.level.progress * 100)}%"
            out += "§7${full(p.level.xpInto.toDouble())} / ${full(p.level.xpNeeded.toDouble())} XP"
        }
        out += "§7Total XP: §f${full(p.xp)}"
        p.heldItem?.let { out += "§7Held item: §a${PvData.pretty(it.removePrefix("PET_ITEM_"))}" }
        if (p.candyUsed > 0) out += "§7Candy used: §f${p.candyUsed}/10"
        p.skin?.let { out += "§7Skin: §d${PvData.pretty(it)}" }
        return out
    }
}
