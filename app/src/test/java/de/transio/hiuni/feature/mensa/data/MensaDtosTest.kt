package de.transio.hiuni.feature.mensa.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MensaDtosTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `parses real STW-ON sample with prices as strings and structured tags`() {
        val sample = """
            {
              "meals": [
                {
                  "id": 148692,
                  "date": "2026-05-26",
                  "name": "Bohnenragout mit Pilzen | Kartoffeln | 1 Stück Obst",
                  "price": { "student": "2.50", "employee": "6.85", "guest": "7.85" },
                  "time": "noon",
                  "lane": { "id": 10, "name": "Essen 1" },
                  "tags": {
                    "categories": [ {"id": "VEGA", "name": "Vegan"} ],
                    "allergens":  [ {"id": "SO",   "name": "Soja"} ],
                    "additives":  [ {"id": "3",    "name": "Antioxidationsmittel"} ],
                    "special":    []
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<MensaMenuResponse>(sample)
        assertEquals(1, parsed.meals.size)
        val entity = parsed.meals.first().toEntity(locationId = 150, fallbackKey = "x")
        assertNotNull(entity)
        requireNotNull(entity)

        assertEquals("148692", entity.sourceId)
        assertEquals(150, entity.locationId)
        assertEquals(LocalDate.of(2026, 5, 26), entity.date)
        assertEquals("Essen 1", entity.category)
        assertEquals(250, entity.priceStudentCents)
        assertEquals(685, entity.priceEmployeeCents)
        assertEquals(785, entity.priceGuestCents)
        assertTrue("tags should include Vegan", entity.tags.contains("Vegan"))
        assertTrue("tags should include mapped allergen *Soja", entity.tags.contains("*Soja"))
    }

    @Test
    fun `evening time prefixes category and missing fields fall back gracefully`() {
        val sample = """
            {
              "meals": [
                {
                  "id": 999,
                  "date": "2026-05-26",
                  "name": " Suppe ",
                  "price": { "student": "1.20", "employee": null, "guest": null },
                  "time": "evening",
                  "lane": null,
                  "tags": null
                }
              ]
            }
        """.trimIndent()

        val entity = json.decodeFromString<MensaMenuResponse>(sample)
            .meals.first()
            .toEntity(locationId = 150, fallbackKey = "fallback-7")
        requireNotNull(entity)

        assertEquals("999", entity.sourceId)
        assertEquals("Abend", entity.category)
        assertEquals("Suppe", entity.name)
        assertEquals(120, entity.priceStudentCents)
        assertTrue(entity.tags.isEmpty())
    }

    @Test
    fun `meal without date is skipped`() {
        val sample = """{ "meals": [ { "name": "Ghost", "date": null } ] }"""
        val response = json.decodeFromString<MensaMenuResponse>(sample)
        assertNull(response.meals.first().toEntity(locationId = 150, fallbackKey = "x"))
    }

    @Test
    fun `closure notice without prices is skipped as announcement`() {
        val sample = """
            {
              "meals": [
                {
                  "id": 999,
                  "date": "2026-05-26",
                  "name": "Vom 25.05.2026 bis 29.05.2026 bleibt die Abendmensa geschlossen.",
                  "price": { "student": null, "employee": null, "guest": null },
                  "time": "evening",
                  "lane": { "id": 10, "name": "Essen 1" },
                  "tags": null
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString<MensaMenuResponse>(sample)
        assertNull(response.meals.first().toEntity(locationId = 150, fallbackKey = "x"))
    }

    @Test
    fun `zero-price entries are skipped as announcements`() {
        val sample = """
            {
              "meals": [
                {
                  "id": 998,
                  "date": "2026-05-26",
                  "name": "Mensa heute geschlossen",
                  "price": { "student": "0.00", "employee": "0.00", "guest": "0.00" },
                  "time": "noon",
                  "lane": null,
                  "tags": null
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString<MensaMenuResponse>(sample)
        assertNull(response.meals.first().toEntity(locationId = 150, fallbackKey = "x"))
    }

    @Test
    fun `priceLabel formats euro string from cents`() {
        val entity = MealEntity(
            sourceId = "x",
            locationId = 150,
            date = LocalDate.now(),
            category = "Hauptgericht",
            name = "Test",
            description = null,
            priceStudentCents = 380,
            priceEmployeeCents = null,
            priceGuestCents = null,
            tags = ""
        )
        // priceLabel uses default locale; allow either '.' or ',' as decimal separator
        val label = entity.priceLabel
        assertTrue("priceLabel was: $label", label.contains("3") && label.contains("80"))
    }
}
