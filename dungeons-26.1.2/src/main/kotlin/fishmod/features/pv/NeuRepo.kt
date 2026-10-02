package fishmod.features.pv

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import fishmod.utils.debug.FishDiag
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

// NEU repo lookups for skull skins + pet names; fetched once per id, cached to disk.
object NeuRepo {
    private const val BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/"
    // Last commit before Hypixel moved many skull items (bait etc.) to resource-pack paper models.
    private const val PRE_RP = "26169fef95cc5614a25e8593b074cb41beb7e8de"
    private val FILE = File("config/fishmod-neu-skins.json")
    private val VALUE = Regex("""Value:\\?"([A-Za-z0-9+/=]{40,})""")
    private val GSON = Gson()

    class Skin(val itemId: String, val texture: String?)
    // id -> "itemid|texture" ("" texture = none); missing = not fetched yet
    private val skins = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    @Volatile var version = 0; private set
    @Volatile private var petNames: Map<String, String>? = null
    private var petNamesAsked = false

    init {
        runCatching {
            if (FILE.exists()) FILE.reader().use { r -> GSON.fromJson<Map<String, String>>(r, object : TypeToken<Map<String, String>>() {}.type)?.let { skins.putAll(it) } }
        }.onFailure { FishDiag.fail("NeuRepo.1", "neu skin cache load failed", it) }
    }

    // Skin for a SkyBlock id; null while loading (version bumps when it lands).
    fun skin(id: String): Skin? {
        skins[id]?.let { v -> val i = v.indexOf('|'); return Skin(v.substring(0, i), v.substring(i + 1).ifEmpty { null }) }
        if (pending.add(id)) fetchSkin(id, PRE_RP)
        return null
    }

    fun petName(type: String): String? {
        petNames?.let { return it[type] }
        if (!petNamesAsked) { petNamesAsked = true; fetchPetNames() }
        return null
    }

    private fun get(url: String, cb: (Int, String?) -> Unit) {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build()
        fishmod.utils.Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r -> cb(r.statusCode(), r.body()) }
            .exceptionally { cb(-1, null); null }
    }

    private fun fetchSkin(id: String, ref: String) {
        val path = URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")
        get("$BASE$ref/items/$path.json") { st, body ->
            if (st == 404 && ref == PRE_RP) { fetchSkin(id, "master"); return@get }
            if (st != 200 || body == null) { pending.remove(id); return@get } // retry on a later view
            val o = runCatching { GSON.fromJson(body, JsonObject::class.java) }.getOrNull()
            val itemId = o?.get("itemid")?.asString ?: "minecraft:paper"
            val tex = o?.get("nbttag")?.asString?.let { VALUE.find(it)?.groupValues?.get(1) } ?: ""
            skins[id] = "$itemId|$tex"
            version++
            save()
        }
    }

    private fun fetchPetNames() {
        get("${BASE}master/constants/pets.json") { st, body ->
            val o = if (st == 200 && body != null) runCatching { GSON.fromJson(body, JsonObject::class.java) }.getOrNull() else null
            val m = o?.getAsJsonObject("id_to_display_name")?.entrySet()?.associate { it.key to it.value.asString }
            if (m == null) { petNamesAsked = false; return@get }
            petNames = m; version++
        }
    }

    @Synchronized private fun save() {
        runCatching { FILE.parentFile?.mkdirs(); FILE.writer().use { GSON.toJson(HashMap(skins), it) } }
            .onFailure { FishDiag.fail("NeuRepo.2", "neu skin cache save failed", it) }
    }
}
