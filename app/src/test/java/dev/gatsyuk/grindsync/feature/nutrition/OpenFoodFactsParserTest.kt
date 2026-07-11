package dev.gatsyuk.grindsync.feature.nutrition

import dev.gatsyuk.grindsync.core.model.FoodSource
import dev.gatsyuk.grindsync.feature.nutrition.data.OpenFoodFactsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsParserTest {

    private val sample = """
        {
          "count": 3,
          "products": [
            {
              "code": "737628064502",
              "product_name": "Peanut Butter",
              "brands": "BrandX, SubBrand",
              "nutriments": {
                "energy-kcal_100g": 588,
                "proteins_100g": 25.1,
                "carbohydrates_100g": 20,
                "fat_100g": 50.4
              }
            },
            {
              "code": "1",
              "product_name": "",
              "nutriments": { "energy-kcal_100g": 100 }
            },
            {
              "code": "2",
              "product_name": "No Energy Data",
              "nutriments": { "proteins_100g": 10 }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid products and normalizes to 100g servings`() {
        val foods = OpenFoodFactsClient.parseSearchResponse(sample)
        assertEquals(1, foods.size) // blank-name and no-kcal products dropped
        val pb = foods.single()
        assertEquals("Peanut Butter", pb.name)
        assertEquals("BrandX", pb.brand) // first brand only
        assertEquals("737628064502", pb.barcode)
        assertEquals(FoodSource.OFF, pb.source)
        assertEquals(100.0, pb.servingSize, 0.0)
        assertEquals("g", pb.servingUnit)
        assertEquals(588.0, pb.kcalPerServing, 0.0)
        assertEquals(25.1, pb.proteinG, 0.0)
        assertEquals(20.0, pb.carbsG, 0.0)
        assertEquals(50.4, pb.fatG, 0.0)
    }

    @Test
    fun `empty or malformed payloads yield empty lists`() {
        assertTrue(OpenFoodFactsClient.parseSearchResponse("""{"count":0}""").isEmpty())
        assertTrue(OpenFoodFactsClient.parseSearchResponse("""{"products":[]}""").isEmpty())
    }
}
