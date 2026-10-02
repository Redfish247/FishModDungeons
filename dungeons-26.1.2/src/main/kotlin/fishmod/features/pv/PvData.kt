package fishmod.features.pv

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.HypixelApi
import fishmod.utils.debug.FishDiag
import fishmod.utils.networth.ItemsDb
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

// ---------- JSON helpers (never throw) ----------
internal fun JsonObject?.obj(vararg path: String): JsonObject? {
    var cur: JsonElement? = this ?: return null
    for (p in path) cur = (cur as? JsonObject)?.get(p) ?: return null
    return cur as? JsonObject
}
internal fun JsonObject?.arr(vararg path: String): JsonArray? {
    if (path.isEmpty()) return null
    return (this.obj(*path.dropLast(1).toTypedArray())?.get(path.last())) as? JsonArray
}
internal fun JsonObject?.prim(vararg path: String): JsonElement? {
    if (this == null || path.isEmpty()) return null
    val e = this.obj(*path.dropLast(1).toTypedArray())?.get(path.last()) ?: return null
    return if (e.isJsonPrimitive) e else null
}
internal fun JsonObject?.num(vararg path: String): Double? = runCatching { prim(*path)?.asDouble }.getOrNull()
internal fun JsonObject?.long(vararg path: String): Long? = num(*path)?.toLong()
internal fun JsonObject?.int(vararg path: String): Int? = num(*path)?.toInt()
internal fun JsonObject?.str(vararg path: String): String? = runCatching { prim(*path)?.asString }.getOrNull()
internal fun JsonObject?.bool(vararg path: String): Boolean? = runCatching { prim(*path)?.asBoolean }.getOrNull()
internal fun JsonObject?.numMap(vararg path: String): Map<String, Double> {
    val o = obj(*path) ?: return emptyMap()
    val out = LinkedHashMap<String, Double>()
    for ((k, v) in o.entrySet()) if (v.isJsonPrimitive) runCatching { out[k] = v.asDouble }
    return out
}

// ---------- Model ----------
class PvSkill(val key: String, val name: String, val xp: Long?, val level: PvTables.Level, val apiDisabled: Boolean)

class PvSlayer(val def: PvTables.Slayer, val xp: Long, val level: Int, val tierKills: IntArray) {
    val totalKills get() = tierKills.sum()
}

class PvFloor(
    val floor: Int, val master: Boolean, val completions: Int, val timesPlayed: Int?, val bestScore: Int?,
    val fastest: Long?, val fastestS: Long?, val fastestSPlus: Long?, val mobsKilled: Int?, val watcherKills: Int?,
    val milestone: Int?, val mostDamage: Double?,
) { val label get() = (if (master) "M" else if (floor == 0) "E" else "F") + if (floor == 0 && !master) "" else floor.toString() }

class PvDungeons(
    val cata: PvTables.Level, val classes: Map<String, PvTables.Level>, val selectedClass: String?,
    val secrets: Long?, val floors: List<PvFloor>, val master: List<PvFloor>, val highestFloor: String?, val raw: JsonObject?,
) {
    val classAverage get() = if (classes.isEmpty()) 0.0 else classes.values.sumOf { minOf(it.fractional, 50.0) } / classes.size
    val totalRuns get() = floors.sumOf { it.completions } + master.sumOf { it.completions }
}

class PvPet(
    val type: String, private val fallbackName: String, val tier: String, val xp: Double, val level: PvTables.Level,
    val heldItem: String?, val active: Boolean, val candyUsed: Int, val skin: String?, val uuid: String?,
) {
    val rarityCode get() = PvTables.RARITY_CODE[tier] ?: "§f"
    val name: String get() = NeuRepo.petName(type) ?: fallbackName
}

class PvAccessory(val item: PvItem, val active: Boolean, val mp: Int)

class PvAccessories(
    val list: List<PvAccessory>, val magicalPower: Int, val highestMagicalPower: Int?, val selectedPower: String?,
    val unlockedPowers: List<String>, val tuning: Map<String, Int>, val bagUpgrades: Int?, val raw: JsonObject?,
) {
    val activeCount get() = list.count { it.active }
    val recombCount get() = list.count { it.item.recombobulated }
    val enrichedCount get() = list.count { it.item.enrichment != null }
}

class PvPowder(val available: Long, val spent: Long) { val total get() = available + spent }

class PvSkillTree(val nodes: Map<String, Int>, val xp: Long?, val selectedAbility: String?, val tokensSpent: Int?, val powder: Map<String, PvPowder>)

class PvCrimson(
    val faction: String?, val mageRep: Int?, val barbRep: Int?, val kuudra: Map<String, Int>,
    val dojoPoints: Map<String, Int>, val dojoTimes: Map<String, Int>, val raw: JsonObject?,
)

class PvRift(
    val motes: Long?, val lifetimeMotes: Long?, val enigmaSouls: Int?, val timecharms: Int?, val porhtals: Int?,
    val montezumaCats: Int?, val inventory: PvInv, val armor: List<PvItem?>, val equipment: PvInv, val enderChest: PvInv, val raw: JsonObject?,
)

class PvMember(
    val uuid: String,
    val raw: JsonObject,
    val skills: List<PvSkill>,
    val skillAverage: Double,
    val skillAverageCosmetic: Double,
    val sbLevel: PvTables.Level,
    val purse: Double?,
    val fairySouls: Int?,
    val fairyExchanges: Int?,
    val firstJoin: Long?,
    val lastSave: Long?,
    val slayers: List<PvSlayer>,
    val dungeons: PvDungeons?,
    val essence: Map<String, Long>,
    val pets: List<PvPet>,
    val accessories: PvAccessories,
    val inventory: PvInv,
    val armor: List<PvItem?>,
    val equipment: PvInv,
    val enderChest: PvInv,
    val backpacks: Map<Int, PvInv>,
    val backpackIcons: Map<Int, PvItem?>,
    val wardrobe: PvInv,
    val wardrobeEquipped: Int?,
    val vault: PvInv,
    val potionBag: PvInv,
    val fishingBag: PvInv,
    val quiver: PvInv,
    val sacks: Map<String, Long>,
    val collections: Map<String, Long>,
    val minions: Map<String, Set<Int>>,
    val bestiaryKills: Map<String, Long>,
    val bestiaryDeaths: Map<String, Long>,
    val hotm: PvSkillTree?,
    val hotf: PvSkillTree?,
    val attributes: Map<String, Long>,
    val crimson: PvCrimson?,
    val rift: PvRift?,
    val playerStats: JsonObject?,
    val inventoryApi: Boolean,
) {
    @Volatile var name: String? = null
    val activePet get() = pets.firstOrNull { it.active }
    // Wardrobe set i: [helm, chest, legs, boots] from the 9x4 page layout.
    fun wardrobeSet(i: Int): List<PvItem?> {
        val page = i / 9; val col = i % 9
        return (0..3).map { r -> wardrobe.items.getOrNull(page * 36 + r * 9 + col) }
    }
    val wardrobeSetCount get() = (wardrobe.size / 36) * 9
    fun enderPage(p: Int): List<PvItem?> = enderChest.items.drop(p * 45).take(45)
    val enderPages get() = (enderChest.size + 44) / 45
}

class PvGarden(val xp: Long?, val plots: Int?, val visitorsServed: Int?, val uniqueVisitors: Int?, val raw: JsonObject)
class PvMuseum(val value: Long?, val appraisal: Boolean?, val donated: Int, val special: Int, val raw: JsonObject)

class PvProfile(
    val id: String, val cuteName: String, val gameMode: String?, val selected: Boolean, val bank: Double?,
    val members: Map<String, PvMember>, val raw: JsonObject,
) {
    @Volatile var garden: PvGarden? = null
    @Volatile var extrasRequested = false
    @Volatile var gardenStatus = "loading"
    @Volatile var museumStatus = "loading"
    val modeIcon get() = when (gameMode) { "ironman" -> "(Iron)"; "bingo" -> "(Bingo)"; "island" -> "(Stranded)"; else -> "" }
}

class PvPlayer(
    val displayName: String?, val rankPrefix: String, val networkExp: Double?, val karma: Long?, val achievementPoints: Long?,
    val firstLogin: Long?, val lastLogin: Long?, val socials: Map<String, String>, val raw: JsonObject,
) {
    val networkLevel get() = networkExp?.let { (Math.sqrt(2 * it + 30625) / 50 - 2.5) }
}

class PvGuild(val name: String, val tag: String?, val memberCount: Int, val raw: JsonObject)

// Whole lookup; async parts fill in later.
class PvResult(val uuid: String, val name: String, val profiles: List<PvProfile>) {
    @Volatile var player: PvPlayer? = null
    @Volatile var playerStatus = "loading"
    @Volatile var guild: PvGuild? = null
    @Volatile var guildStatus = "loading"
    val selected get() = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
}

// Local networth engine (HypixelApi.networthBreakdown), computed off-thread once per profile member.
class PvNw(@Volatile var status: String = "loading", @Volatile var total: Double = 0.0, @Volatile var parts: Map<String, Double> = emptyMap())

object PvNetworth {
    private val cache = ConcurrentHashMap<String, PvNw>()
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-PvNetworth").apply { isDaemon = true } }

    fun get(p: PvProfile, uuid: String): PvNw = cache.computeIfAbsent(p.id + ":" + uuid) {
        val nw = PvNw()
        exec.execute {
            try {
                val bd = HypixelApi.networthBreakdown(p.raw, uuid)
                nw.parts = bd; nw.total = bd.values.sum(); nw.status = "ok"
            } catch (e: Exception) {
                FishDiag.fail("PvData.nw", "networth compute failed", e); nw.status = "unavailable"
            }
        }
        nw
    }
}

class PvLoad(val query: String?) {
    enum class State { LOADING, ERROR, READY }
    @Volatile var state = State.LOADING
    @Volatile var message = "Resolving player…"
    @Volatile var result: PvResult? = null
}

object PvData {

    private val names = ConcurrentHashMap<String, String>()
    private val namesPending = ConcurrentHashMap.newKeySet<String>()

    fun load(nameOrNull: String?): PvLoad {
        val load = PvLoad(nameOrNull)
        runCatching { ItemsDb.ensureLoaded() }
        val mc = Minecraft.getInstance()
        val name = nameOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: mc.user.name
        if (!name.matches(Regex("^\\w{1,16}$"))) { fail(load, "Invalid username: $name"); return load }
        HypixelApi.resolveUuidAsync(name) { uuid ->
            if (uuid == null) { fail(load, "Player '$name' not found"); return@resolveUuidAsync }
            names[uuid] = name
            load.message = "Loading profiles…"
            HypixelApi.proxyGet("/skyblock/profiles?uuid=$uuid") { status, body ->
                val res = FishDiag.guard("PvData.1", "profile parse failed for $name") { parseProfiles(uuid, name, status, body, load) }
                if (res == null) { if (load.state == PvLoad.State.LOADING) fail(load, "Couldn't read profiles (HTTP $status)"); return@proxyGet }
                load.result = res
                load.state = PvLoad.State.READY
                fetchExtras(res)
                res.selected?.let { ensureProfileExtras(it) }
            }
        }
        return load
    }

    private fun fail(load: PvLoad, msg: String) { load.message = msg; load.state = PvLoad.State.ERROR }

    private fun parseJson(body: String?): JsonObject? = runCatching { JsonParser.parseString(body ?: return null).asJsonObject }.getOrNull()

    private fun parseProfiles(uuid: String, name: String, status: Int, body: String?, load: PvLoad): PvResult? {
        if (status == 429) { fail(load, "Rate limited, try again shortly"); return null }
        if (status == 403) { fail(load, "API access blocked"); return null }
        val root = parseJson(body) ?: run { fail(load, "Hypixel proxy unreachable (HTTP $status)"); return null }
        if (root.bool("success") == false) { fail(load, root.str("cause") ?: "API error"); return null }
        val arr = root.getAsJsonArray("profiles")
        if (arr == null || arr.isEmpty) { fail(load, "$name has no SkyBlock profiles"); return null }
        val all = arr.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            FishDiag.guard("PvData.2", "profile entry parse failed") { parseProfile(p) }
        }
        // Drop profiles the player left (they no longer appear as a current member).
        val profiles = all.filter { uuid in it.members }.ifEmpty { all }
        val res = PvResult(uuid, name, profiles)
        for (p in profiles) for (m in p.members.values) m.name = names[m.uuid]
        return res
    }

    // Left/kicked co-op members keep a deletion_notice; pending invites are unconfirmed.
    private fun removedMember(m: JsonObject): Boolean =
        m.obj("profile", "deletion_notice") != null || m.obj("deletion_notice") != null ||
            m.bool("profile", "coop_invitation", "confirmed") == false || m.bool("coop_invitation", "confirmed") == false

    private fun parseProfile(p: JsonObject): PvProfile {
        val members = LinkedHashMap<String, PvMember>()
        p.obj("members")?.entrySet()?.forEach { (uuid, el) ->
            val m = el as? JsonObject ?: return@forEach
            if (removedMember(m)) return@forEach
            FishDiag.guard("PvData.3", "member parse failed $uuid") { parseMember(uuid, m) }?.let { members[uuid] = it }
        }
        return PvProfile(
            id = p.str("profile_id") ?: "", cuteName = p.str("cute_name") ?: "Profile", gameMode = p.str("game_mode"),
            selected = p.bool("selected") ?: false, bank = p.num("banking", "balance"), members = members, raw = p,
        )
    }

    private fun inv(m: JsonObject, vararg path: String): PvInv {
        val data = m.str(*path, "data") ?: return PvInv.EMPTY
        return PvInv(FishDiag.guard("PvData.4", "inventory decode failed ${path.joinToString(".")}") { PvItem.decode(data) } ?: emptyList())
    }

    private fun parseMember(uuid: String, m: JsonObject): PvMember {
        val exp = m.obj("player_data", "experience")
        val farmingBonus = m.int("jacobs_contest", "perks", "farming_level_cap") ?: m.int("player_data", "perks", "farming_level_cap") ?: 0
        val tamingBonus = m.arr("pets_data", "pet_care", "pet_types_sacrificed")?.size() ?: 0
        val skills = PvTables.SKILLS.map { k ->
            val xp = exp.long("SKILL_" + k.uppercase())
            val cap = PvTables.skillCap(k, farmingBonus, tamingBonus, exp.int("SKILL_FORAGING_extra_level_cap") ?: 0)
            PvSkill(k, k.replaceFirstChar { it.uppercase() }, xp, PvTables.skill(k, xp ?: 0, cap), xp == null)
        }
        val main = skills.filter { it.key !in PvTables.COSMETIC_SKILLS }
        val avg = if (main.isEmpty()) 0.0 else main.sumOf { it.level.fractional } / main.size
        val avgC = if (skills.isEmpty()) 0.0 else skills.sumOf { it.level.fractional } / skills.size

        val slayers = PvTables.SLAYERS.map { d ->
            val o = m.obj("slayer", "slayer_bosses", d.key)
            val xp = o.long("xp") ?: 0
            PvSlayer(d, xp, PvTables.slayerLevel(d, xp), IntArray(d.tiers) { o.int("boss_kills_tier_$it") ?: 0 })
        }

        val pets = m.arr("pets_data", "pets")?.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val type = p.str("type") ?: return@mapNotNull null
            val baseTier = p.str("tier") ?: "COMMON"
            // Tier Boost raises rarity (and the level table offset) by one.
            val tier = if (p.str("heldItem") == "PET_ITEM_TIER_BOOST") RARITY_ORDER.getOrNull(RARITY_ORDER.indexOf(baseTier) + 1) ?: baseTier else baseTier
            val xp = p.num("exp") ?: 0.0
            PvPet(type, pretty(type), tier, xp, PvTables.petLevel(xp, tier, type), p.str("heldItem"),
                p.bool("active") ?: false, p.int("candyUsed") ?: 0, p.str("skin"), p.str("uuid"))
        }?.sortedWith(compareByDescending<PvPet> { it.active }.thenByDescending { RARITY_ORDER.indexOf(it.tier) }.thenByDescending { it.xp }) ?: emptyList()

        val bagRoot = m.obj("inventory", "bag_contents")
        val talis = inv(m, "inventory", "bag_contents", "talisman_bag")
        val seen = HashSet<String>()
        val acc = talis.items.filterNotNull().map { it ->
            val dup = it.id != null && !seen.add(it.id)
            PvAccessory(it, !dup, if (dup) 0 else PvTables.RARITY_MP[it.rarity] ?: 0)
        }.sortedByDescending { RARITY_ORDER.indexOf(it.item.rarity ?: "") }
        val abs = m.obj("accessory_bag_storage")
        val tuning = HashMap<String, Int>()
        abs.obj("tuning", "slot_0")?.entrySet()?.forEach { (k, v) -> runCatching { tuning[k] = v.asInt } }
        val accessories = PvAccessories(
            acc, acc.sumOf { it.mp }, abs.int("highest_magical_power"), abs.str("selected_power"),
            abs.arr("unlocked_powers")?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList(),
            tuning, abs.int("bag_upgrades_purchased"), abs,
        )

        val armorInv = inv(m, "inventory", "inv_armor")
        val backpacks = HashMap<Int, PvInv>()
        m.obj("inventory", "backpack_contents")?.entrySet()?.forEach { (k, _) ->
            k.toIntOrNull()?.let { i -> backpacks[i] = inv(m, "inventory", "backpack_contents", k) }
        }
        val bpIcons = HashMap<Int, PvItem?>()
        m.obj("inventory", "backpack_icons")?.entrySet()?.forEach { (k, _) ->
            k.toIntOrNull()?.let { i -> bpIcons[i] = inv(m, "inventory", "backpack_icons", k).items.firstOrNull() }
        }

        val sacks = (m.numMap("inventory", "sacks_counts").ifEmpty { m.numMap("sacks_counts") }).mapValues { it.value.toLong() }
        val collections = m.numMap("collection").mapValues { it.value.toLong() }
        val minions = HashMap<String, MutableSet<Int>>()
        m.arr("player_data", "crafted_generators")?.forEach { el ->
            val s = runCatching { el.asString }.getOrNull() ?: return@forEach
            val i = s.lastIndexOf('_'); if (i <= 0) return@forEach
            val t = s.substring(i + 1).toIntOrNull() ?: return@forEach
            minions.getOrPut(s.substring(0, i)) { HashSet() }.add(t)
        }

        val essence = HashMap<String, Long>()
        m.obj("currencies", "essence")?.entrySet()?.forEach { (k, v) -> (v as? JsonObject).long("current")?.let { essence[k] = it } }

        val dungeons = m.obj("dungeons")?.let { d -> parseDungeons(d) }

        val treeNodes = m.obj("skill_tree", "nodes")
        val core = m.obj("mining_core")
        val powder = HashMap<String, PvPowder>()
        for (t in listOf("mithril", "gemstone", "glacite")) {
            val av = core.long("powder_$t") ?: m.long("skill_tree", "powder_$t") ?: continue
            val sp = core.long("powder_spent_$t") ?: m.long("skill_tree", "powder_spent_$t") ?: 0
            powder[t] = PvPowder(av, sp)
        }
        fun intMap(o: JsonObject?): Map<String, Int> = o?.entrySet()?.mapNotNull { (k, v) -> runCatching { k to v.asInt }.getOrNull() }?.toMap() ?: emptyMap()
        val hotmNodes = intMap(treeNodes.obj("mining")).ifEmpty { intMap(core.obj("nodes")) }
        val hotm = if (hotmNodes.isEmpty() && powder.isEmpty()) null else PvSkillTree(
            hotmNodes, m.long("skill_tree", "experience", "mining") ?: core.long("experience"),
            m.str("skill_tree", "selected_ability", "mining") ?: core.str("selected_pickaxe_ability"),
            m.int("skill_tree", "tokens_spent", "mining") ?: core.int("tokens_spent"), powder,
        )
        val hotfNodes = intMap(treeNodes.obj("foraging"))
        val hotf = if (hotfNodes.isEmpty()) null else PvSkillTree(
            hotfNodes, m.long("skill_tree", "experience", "foraging"), m.str("skill_tree", "selected_ability", "foraging"),
            m.int("skill_tree", "tokens_spent", "foraging"), emptyMap(),
        )

        val attributes = (m.numMap("attributes", "stacks").ifEmpty { m.numMap("attributes") }).mapValues { it.value.toLong() }

        val nether = m.obj("nether_island_player_data")
        val crimson = nether?.let { n ->
            val dojo = n.obj("dojo")
            PvCrimson(
                n.str("selected_faction"), n.int("mages_reputation"), n.int("barbarians_reputation"),
                n.numMap("kuudra_completed_tiers").mapValues { it.value.toInt() },
                dojo?.entrySet()?.filter { it.key.startsWith("dojo_points_") }?.mapNotNull { (k, v) -> runCatching { k.removePrefix("dojo_points_") to v.asInt }.getOrNull() }?.toMap() ?: emptyMap(),
                dojo?.entrySet()?.filter { it.key.startsWith("dojo_time_") }?.mapNotNull { (k, v) -> runCatching { k.removePrefix("dojo_time_") to v.asInt }.getOrNull() }?.toMap() ?: emptyMap(),
                n,
            )
        }

        val riftO = m.obj("rift")
        val rift = riftO?.let { r ->
            val riftArmor = inv(m, "rift", "inventory", "inv_armor")
            PvRift(
                m.long("currencies", "motes_purse"), m.long("player_stats", "rift", "lifetime_motes_earned"),
                r.arr("enigma", "found_souls")?.size(), r.arr("gallery", "secured_trophies")?.size(),
                r.arr("village_plaza", "porhtals")?.size() ?: r.obj("porhtal")?.entrySet()?.size,
                r.obj("dead_cats")?.arr("found_cats")?.size(),
                inv(m, "rift", "inventory", "inv_contents"), riftArmor.items.reversed(),
                inv(m, "rift", "inventory", "equipment_contents"), inv(m, "rift", "inventory", "ender_chest_contents"), r,
            )
        }

        val invMain = inv(m, "inventory", "inv_contents")
        return PvMember(
            uuid = uuid, raw = m, skills = skills, skillAverage = avg, skillAverageCosmetic = avgC,
            sbLevel = PvTables.sbLevel(m.num("leveling", "experience") ?: 0.0),
            purse = m.num("currencies", "coin_purse"),
            fairySouls = m.int("fairy_soul", "total_collected"), fairyExchanges = m.int("fairy_soul", "fairy_exchanges"),
            firstJoin = m.long("profile", "first_join"), lastSave = m.long("profile", "last_save") ?: m.long("last_save"),
            slayers = slayers, dungeons = dungeons, essence = essence, pets = pets, accessories = accessories,
            inventory = invMain, armor = armorInv.items.reversed(), equipment = inv(m, "inventory", "equipment_contents"),
            enderChest = inv(m, "inventory", "ender_chest_contents"), backpacks = backpacks, backpackIcons = bpIcons,
            wardrobe = inv(m, "inventory", "wardrobe_contents"), wardrobeEquipped = m.int("inventory", "wardrobe_equipped_slot"),
            vault = inv(m, "inventory", "personal_vault_contents"),
            potionBag = if (bagRoot == null) PvInv.EMPTY else inv(m, "inventory", "bag_contents", "potion_bag"),
            fishingBag = if (bagRoot == null) PvInv.EMPTY else inv(m, "inventory", "bag_contents", "fishing_bag"),
            quiver = if (bagRoot == null) PvInv.EMPTY else inv(m, "inventory", "bag_contents", "quiver"),
            sacks = sacks, collections = collections, minions = minions,
            bestiaryKills = m.numMap("bestiary", "kills").filterKeys { it != "last_killed_mob" }.mapValues { it.value.toLong() },
            bestiaryDeaths = m.numMap("bestiary", "deaths").mapValues { it.value.toLong() },
            hotm = hotm, hotf = hotf, attributes = attributes, crimson = crimson, rift = rift,
            playerStats = m.obj("player_stats"), inventoryApi = m.obj("inventory", "inv_contents") != null,
        )
    }

    private fun parseDungeons(d: JsonObject): PvDungeons {
        fun floors(type: String, master: Boolean): List<PvFloor> {
            val t = d.obj("dungeon_types", type) ?: return emptyList()
            return (if (master) 1..7 else 0..7).map { f ->
                val k = f.toString()
                PvFloor(
                    f, master, t.int("tier_completions", k) ?: 0, t.int("times_played", k), t.int("best_score", k),
                    t.long("fastest_time", k), t.long("fastest_time_s", k), t.long("fastest_time_s_plus", k),
                    t.int("mobs_killed", k), t.int("watcher_kills", k), t.int("milestone_completions", k), PvTables.CLASSES.mapNotNull { c -> t.num("most_damage_$c", k) }.maxOrNull(),
                )
            }
        }
        val classes = PvTables.CLASSES.associateWith { c -> PvTables.dungeonOverflow(d.long("player_classes", c, "experience") ?: 0) }
        val f = floors("catacombs", false); val mm = floors("master_catacombs", true)
        val highest = mm.lastOrNull { it.completions > 0 }?.label ?: f.lastOrNull { it.completions > 0 }?.label
        return PvDungeons(
            PvTables.dungeonOverflow(d.long("dungeon_types", "catacombs", "experience") ?: 0), classes,
            d.str("selected_dungeon_class"), d.long("secrets"), f, mm, highest, d,
        )
    }

    // Player, guild, networth for the looked-up uuid.
    private fun fetchExtras(res: PvResult) {
        HypixelApi.getPlayer(res.uuid) { st, body ->
            val p = if (st == 200) parseJson(body)?.obj("player") else null
            if (p == null) { res.playerStatus = "unavailable"; return@getPlayer }
            res.player = FishDiag.guard("PvData.5", "player parse failed") { parsePlayer(p) }
            res.playerStatus = if (res.player != null) "ok" else "unavailable"
        }
        HypixelApi.getGuild(res.uuid) { st, body ->
            val root = if (st == 200) parseJson(body) else null
            if (root == null) { res.guildStatus = "unavailable"; return@getGuild }
            val g = root.obj("guild")
            res.guild = g?.let { PvGuild(it.str("name") ?: "?", it.str("tag"), it.arr("members")?.size() ?: 0, it) }
            res.guildStatus = if (g == null) "none" else "ok"
        }
    }

    // Museum + garden are per profile; fetched once on first view.
    fun ensureProfileExtras(p: PvProfile) {
        if (p.extrasRequested || p.id.isEmpty()) return
        p.extrasRequested = true
        HypixelApi.proxyGet("/skyblock/museum?profile=${p.id}") { st, body ->
            val root = if (st == 200) parseJson(body) else null
            if (root == null) { p.museumStatus = "unavailable"; return@proxyGet }
            p.museumStatus = "ok"
            museumRoots[p.id] = root
        }
        HypixelApi.proxyGet("/skyblock/garden?profile=${p.id}") { st, body ->
            val g = if (st == 200) parseJson(body)?.obj("garden") else null
            if (g == null) { p.gardenStatus = "unavailable"; return@proxyGet }
            p.garden = PvGarden(g.long("garden_experience"), g.arr("unlocked_plots_ids")?.size(),
                g.int("commission_data", "total_completed"), g.int("commission_data", "unique_npcs_served"), g)
            p.gardenStatus = "ok"
        }
    }

    private val museumRoots = ConcurrentHashMap<String, JsonObject>()

    // Museum for one member of a profile (null until loaded / not donated).
    private val museums = ConcurrentHashMap<String, PvMuseum>()
    fun museumFor(p: PvProfile, uuid: String): PvMuseum? {
        museums[p.id + uuid]?.let { return it }
        val m = museumRoots[p.id].obj("members", uuid) ?: return null
        return PvMuseum(m.long("value"), m.bool("appraisal"), m.obj("items")?.entrySet()?.size ?: 0, m.arr("special")?.size() ?: 0, m)
            .also { museums[p.id + uuid] = it }
    }

    private fun parsePlayer(p: JsonObject): PvPlayer {
        val socials = HashMap<String, String>()
        p.obj("socialMedia", "links")?.entrySet()?.forEach { (k, v) -> runCatching { socials[k] = v.asString } }
        return PvPlayer(p.str("displayname"), rankPrefix(p), p.num("networkExp"), p.long("karma"), p.long("achievementPoints"),
            p.long("firstLogin"), p.long("lastLogin"), socials, p)
    }

    private val COLOR_CODES = mapOf(
        "BLACK" to "0", "DARK_BLUE" to "1", "DARK_GREEN" to "2", "DARK_AQUA" to "3", "DARK_RED" to "4", "DARK_PURPLE" to "5",
        "GOLD" to "6", "GRAY" to "7", "DARK_GRAY" to "8", "BLUE" to "9", "GREEN" to "a", "AQUA" to "b", "RED" to "c",
        "LIGHT_PURPLE" to "d", "YELLOW" to "e", "WHITE" to "f",
    )

    fun rankPrefix(p: JsonObject): String {
        p.str("prefix")?.let { return it }
        val plus = "§" + (COLOR_CODES[p.str("rankPlusColor") ?: "RED"] ?: "c")
        when (p.str("rank")) {
            "YOUTUBER" -> return "§c[§fYOUTUBE§c]"
            "ADMIN" -> return "§c[ADMIN]"
            "GAME_MASTER" -> return "§2[GM]"
        }
        if (p.str("monthlyPackageRank") == "SUPERSTAR") {
            val br = "§" + (COLOR_CODES[p.str("monthlyRankColor") ?: "GOLD"] ?: "6")
            return "$br[MVP$plus++$br]"
        }
        return when (p.str("newPackageRank") ?: p.str("packageRank")) {
            "MVP_PLUS" -> "§b[MVP$plus+§b]"
            "MVP" -> "§b[MVP]"
            "VIP_PLUS" -> "§a[VIP§6+§a]"
            "VIP" -> "§a[VIP]"
            else -> "§7"
        }
    }

    // uuid -> name via Mojang session server, async + cached.
    fun nameFor(uuid: String): String? {
        names[uuid]?.let { return it }
        if (namesPending.add(uuid)) {
            runCatching {
                val req = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$uuid"))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "FishMod/1.0").GET().build()
                fishmod.utils.Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
                    parseJson(r.body()).str("name")?.let { names[uuid] = it }
                }
            }.onFailure { FishDiag.fail("PvData.6", "name lookup failed $uuid", it) }
        }
        return null
    }

    val RARITY_ORDER = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL")

    fun pretty(id: String): String = id.lowercase().split('_').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
