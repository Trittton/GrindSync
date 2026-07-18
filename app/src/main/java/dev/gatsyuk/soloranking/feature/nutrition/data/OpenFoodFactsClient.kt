package dev.gatsyuk.soloranking.feature.nutrition.data

import dev.gatsyuk.soloranking.core.database.entity.FoodItemEntity
import dev.gatsyuk.soloranking.core.model.FoodSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open Food Facts search (SPEC §8.1). The ONLY network call in the app, made
 * exclusively on an explicit user action (NFR-8). Results are normalized to a
 * 100 g serving; data quality is patchy by design — the UI always lets the
 * user override values (§8.3).
 */
@Singleton
class OpenFoodFactsClient @Inject constructor() {

    suspend fun search(query: String): Result<List<FoodItemEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl" +
                    "?search_terms=$encoded&search_simple=1&action=process&json=1" +
                    "&page_size=20&fields=code,product_name,brands,nutriments",
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "SoloRanking/0.7 (personal Android app)")
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw IllegalStateException(
                        "Open Food Facts returned HTTP $code: ${error?.take(200)}",
                    )
                }
                connection.inputStream.bufferedReader().use { reader ->
                    parseSearchResponse(reader.readText())
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Pure parser, unit-tested separately from the network call. */
        fun parseSearchResponse(body: String): List<FoodItemEntity> {
            val root = json.parseToJsonElement(body).jsonObject
            val products = root["products"]?.jsonArray ?: return emptyList()
            return products.mapNotNull { element ->
                val product = element.jsonObject
                val name = product["product_name"]?.jsonPrimitive?.content
                    ?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val nutriments = product["nutriments"]?.jsonObject ?: return@mapNotNull null
                fun num(key: String): Double? =
                    nutriments[key]?.jsonPrimitive?.content?.toDoubleOrNull()
                // kcal per 100 g is mandatory; macros default to 0 when absent.
                val kcal = num("energy-kcal_100g") ?: return@mapNotNull null
                FoodItemEntity(
                    name = name,
                    brand = product["brands"]?.jsonPrimitive?.content
                        ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                    source = FoodSource.OFF,
                    barcode = product["code"]?.jsonPrimitive?.content,
                    servingSize = 100.0,
                    servingUnit = "g",
                    kcalPerServing = kcal,
                    proteinG = num("proteins_100g") ?: 0.0,
                    carbsG = num("carbohydrates_100g") ?: 0.0,
                    fatG = num("fat_100g") ?: 0.0,
                )
            }
        }
    }
}
