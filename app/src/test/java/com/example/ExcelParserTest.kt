package com.example

import com.example.util.ExcelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelParserTest {

    @Test
    fun testNormalizeCode() {
        assertEquals("adyasha", ExcelParser.normalizeCode("ADYASHA"))
        assertEquals("adyasha", ExcelParser.normalizeCode("ady_asha"))
        assertEquals("adyasha", ExcelParser.normalizeCode("ADY-ASHA"))
        assertEquals("ady101", ExcelParser.normalizeCode(" ADY - 101 "))
    }

    @Test
    fun testSplitColours() {
        val colors = ExcelParser.splitColours("APRICOT/YELLOW")
        assertEquals(2, colors.size)
        assertEquals("apricot", colors[0])
        assertEquals("yellow", colors[1])

        val compound = ExcelParser.splitColours("BLUE, WHITE + GOLD")
        assertTrue(compound.contains("blue"))
        assertTrue(compound.contains("white"))
        assertTrue(compound.contains("gold"))
    }

    @Test
    fun testSampleCatalog() {
        val sample = ExcelParser.getSampleCatalog()
        assertTrue(sample.isNotEmpty())
        assertTrue(sample.any { it.name == "ADYASHA" })
        assertTrue(sample.any { it.code == "ADY-001" })
    }
}
