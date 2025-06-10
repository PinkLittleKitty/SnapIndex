import android.content.Context
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EmbeddingCacheData(val embeddings: Map<String, List<Float>>)

object EmbeddingCacheManager {

    private const val CACHE_FILENAME = "embedding_cache.json"

    suspend fun loadCache(context: Context): MutableMap<String, FloatArray> {
        val cacheFile = File(context.filesDir, CACHE_FILENAME)
        if (!cacheFile.exists()) return mutableMapOf()

        return try {
            val jsonString = cacheFile.readText()
            val cacheData = Json.decodeFromString<EmbeddingCacheData>(jsonString)
            cacheData.embeddings.mapValues { it.value.toFloatArray() }.toMutableMap()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableMapOf()
        }
    }

    suspend fun saveCache(context: Context, cache: Map<String, FloatArray>) {
        val cacheFile = File(context.filesDir, CACHE_FILENAME)
        try {
            val serializableMap = cache.mapValues { it.value.toList() }
            val cacheData = EmbeddingCacheData(serializableMap)
            val jsonString = Json.encodeToString(cacheData)
            cacheFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
