package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParsedExcelRow(
    val code: String,
    val name: String,
    val colour: String,
    val rowNumber: Int
)

object ExcelParser {

    /**
     * Normalizes a color string for consistent matching.
     */
    fun normalizeColour(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /**
     * Splits compound color strings such as "APRICOT/YELLOW" or "RED, BLUE" into individual colors.
     */
    fun splitColours(value: String): List<String> {
        return value.split(Regex("[/,&+|]+"))
            .map { normalizeColour(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Normalizes a code for matching (e.g. "ADYASHA", "adyasha", "ady_asha", "ADY-ASHA" -> "adyasha").
     */
    fun normalizeCode(value: String): String {
        return value.trim().lowercase().replace(Regex("[ _-]+"), "")
    }

    /**
     * Parses an Excel (.xlsx, .xls) or CSV file from a Content Uri.
     */
    fun parse(context: Context, uri: Uri): List<ParsedExcelRow> {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = getFileName(context, uri).lowercase()

        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                if (fileName.endsWith(".csv") || fileName.endsWith(".tsv") || mimeType.contains("csv")) {
                    parseCsv(stream)
                } else {
                    // Try parsing as XLSX (ZIP)
                    parseXlsx(stream)
                }
            } ?: emptyList()
        } catch (e: Exception) {
            // Fallback: try CSV parsing if XLSX failed
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    parseCsv(stream)
                } ?: emptyList()
            } catch (ignored: Exception) {
                emptyList()
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "catalog.xlsx"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    /**
     * Parses OOXML .xlsx files using streaming ZipInputStream and XmlPullParser.
     */
    private fun parseXlsx(inputStream: InputStream): List<ParsedExcelRow> {
        var sharedStrings: List<String> = emptyList()
        val sheetBytesMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings = parseSharedStrings(zis.readBytes().inputStream())
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetBytesMap[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Pick sheet1.xml, or the first sheet available
        val sheetData = sheetBytesMap["xl/worksheets/sheet1.xml"]
            ?: sheetBytesMap.values.firstOrNull()
            ?: return emptyList()

        return parseSheetXml(sheetData.inputStream(), sharedStrings)
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var inSi = false
        var currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            inSi = true
                            currentText = StringBuilder()
                        }
                        "t" -> {
                            if (inSi) {
                                currentText.append(parser.nextText())
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(currentText.toString())
                        inSi = false
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): List<ParsedExcelRow> {
        val rows = mutableListOf<Map<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableMapOf<Int, String>()
        var currentCellCol = -1
        var currentCellType = ""
        var cellValue = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableMapOf()
                        }
                        "c" -> {
                            val r = parser.getAttributeValue(null, "r") ?: ""
                            currentCellCol = colRefToIndex(r)
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v" -> {
                            cellValue = parser.nextText()
                        }
                        "t" -> {
                            cellValue = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            if (currentCellCol >= 0) {
                                val resolvedValue = when (currentCellType) {
                                    "s" -> {
                                        val idx = cellValue.toIntOrNull() ?: -1
                                        if (idx in sharedStrings.indices) sharedStrings[idx] else ""
                                    }
                                    else -> cellValue
                                }
                                currentRow[currentCellCol] = resolvedValue.trim()
                            }
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (rows.isEmpty()) return emptyList()

        // Detect column positions
        val headerRow = rows.first()
        var codeCol = -1
        var nameCol = -1
        var colourCol = -1

        for ((colIdx, value) in headerRow) {
            val norm = value.uppercase().trim().replace(Regex("\\s+"), " ")
            if (norm == "CODE" && codeCol == -1) codeCol = colIdx
            if (norm == "NAME" && nameCol == -1) nameCol = colIdx
            if ((norm == "TOP COLOR" || norm == "TOP COLOUR" || norm == "COLOUR" || norm == "COLOR") && colourCol == -1) {
                colourCol = colIdx
            }
        }

        // Fallbacks per specification: Column B = 1 (CODE), Column C = 2 (NAME), Column E = 4 (TOP COLOR)
        if (codeCol == -1) codeCol = 1
        if (nameCol == -1) nameCol = 2
        if (colourCol == -1) colourCol = 4

        val result = mutableListOf<ParsedExcelRow>()
        // Skip header row
        for (i in 1 until rows.size) {
            val row = rows[i]
            val code = row[codeCol]?.trim() ?: ""
            val name = row[nameCol]?.trim() ?: ""
            val colour = row[colourCol]?.trim() ?: ""

            if (code.isNotEmpty() && name.isNotEmpty()) {
                result.add(
                    ParsedExcelRow(
                        code = code,
                        name = name,
                        colour = colour,
                        rowNumber = i + 1
                    )
                )
            }
        }
        return result
    }

    /**
     * Converts a cell reference like "A1", "B2", "AA10" to 0-based column index (A->0, B->1, etc.)
     */
    private fun colRefToIndex(cellRef: String): Int {
        var col = 0
        var foundLetters = false
        for (ch in cellRef.uppercase()) {
            if (ch in 'A'..'Z') {
                foundLetters = true
                col = col * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return if (foundLetters) col - 1 else -1
    }

    /**
     * Parses simple CSV / TSV text.
     */
    private fun parseCsv(stream: InputStream): List<ParsedExcelRow> {
        val reader = BufferedReader(InputStreamReader(stream))
        val lines = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()

        val delimiter = if (lines[0].contains("\t")) "\t" else if (lines[0].contains(";")) ";" else ","
        val headers = lines[0].split(delimiter).map { it.trim().removeSurrounding("\"").uppercase() }

        var codeCol = headers.indexOfFirst { it == "CODE" }
        var nameCol = headers.indexOfFirst { it == "NAME" }
        var colourCol = headers.indexOfFirst { it == "TOP COLOR" || it == "TOP COLOUR" || it == "COLOUR" || it == "COLOR" }

        if (codeCol == -1) codeCol = if (headers.size > 1) 1 else 0
        if (nameCol == -1) nameCol = if (headers.size > 2) 2 else 0
        if (colourCol == -1) colourCol = if (headers.size > 4) 4 else if (headers.size > 3) 3 else -1

        val result = mutableListOf<ParsedExcelRow>()
        for (i in 1 until lines.size) {
            val parts = lines[i].split(delimiter).map { it.trim().removeSurrounding("\"") }
            val code = parts.getOrNull(codeCol) ?: ""
            val name = parts.getOrNull(nameCol) ?: ""
            val colour = if (colourCol >= 0) parts.getOrNull(colourCol) ?: "" else ""

            if (code.isNotEmpty() && name.isNotEmpty()) {
                result.add(
                    ParsedExcelRow(
                        code = code,
                        name = name,
                        colour = colour,
                        rowNumber = i + 1
                    )
                )
            }
        }
        return result
    }

    /**
     * Generates a sample catalog for instant testing and demonstration.
     */
    fun getSampleCatalog(): List<ParsedExcelRow> {
        return listOf(
            ParsedExcelRow("ADY-001", "ADYASHA", "NAVY BLUE", 2),
            ParsedExcelRow("ADY-002", "ADYASHA", "APRICOT/YELLOW", 3),
            ParsedExcelRow("ADY-003", "ADYASHA", "ROSE PINK", 4),
            ParsedExcelRow("ADY-004", "ADYASHA", "EMERALD GREEN", 5),
            ParsedExcelRow("ADY-005", "ADYASHA", "BLACK", 6),
            ParsedExcelRow("ADY-006", "ADYASHA", "BURGUNDY", 7),
            ParsedExcelRow("FLR-101", "FLORA", "ROSE PINK", 8),
            ParsedExcelRow("FLR-102", "FLORA", "WHITE/GOLD", 9),
            ParsedExcelRow("FLR-103", "FLORA", "LAVENDER", 10),
            ParsedExcelRow("FLR-104", "FLORA", "SKY BLUE", 11),
            ParsedExcelRow("SLK-201", "SILK BREEZE", "ROYAL BLUE", 12),
            ParsedExcelRow("SLK-202", "SILK BREEZE", "APRICOT/YELLOW", 13),
            ParsedExcelRow("SLK-203", "SILK BREEZE", "PEACH", 14),
            ParsedExcelRow("SLK-204", "SILK BREEZE", "EMERALD GREEN", 15),
            ParsedExcelRow("VNT-301", "VINTAGE GLAM", "BLACK", 16),
            ParsedExcelRow("VNT-302", "VINTAGE GLAM", "WHITE/GOLD", 17),
            ParsedExcelRow("VNT-303", "VINTAGE GLAM", "BURGUNDY", 18),
            ParsedExcelRow("VNT-304", "VINTAGE GLAM", "NAVY BLUE", 19),
            ParsedExcelRow("AUR-401", "AURORA", "LAVENDER", 20),
            ParsedExcelRow("AUR-402", "AURORA", "SKY BLUE", 21),
            ParsedExcelRow("AUR-403", "AURORA", "CORAL", 22),
            ParsedExcelRow("AUR-404", "AURORA", "MINT GREEN", 23)
        )
    }
}
