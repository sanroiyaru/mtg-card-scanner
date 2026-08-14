package com.example.mtgscanner

import com.google.mlkit.vision.text.Text

data class OcrCandidate(
    val cardName: String,
    val setCode: String,
    val collectorNumber: String,
    val language: String
)

object OcrParser {
    private val fractionPattern = Regex("""\b(\d{1,4}[a-z]?)\s*/\s*\d{1,4}\b""", RegexOption.IGNORE_CASE)
    private val languagePattern = Regex("""\b(JP|JA|EN|DE|FR|IT|ES|PT|KO|RU|ZHS|ZHT)\b""", RegexOption.IGNORE_CASE)
    private val codePattern = Regex("""\b[A-Z0-9]{3,5}\b""")
    private val ignoredCodes = setOf("THE", "ART", "ILLUS", "WIZARDS", "MTG")

    fun parse(visionText: Text, imageHeight: Int): OcrCandidate {
        val fullText = visionText.text
        val lines = visionText.textBlocks.flatMap { it.lines }
        val collectorLine = lines.firstOrNull { fractionPattern.containsMatchIn(it.text) }
        val collector = fractionPattern.find(collectorLine?.text.orEmpty())?.groupValues?.get(1)
            ?: fractionPattern.find(fullText)?.groupValues?.get(1).orEmpty()

        val nearbyText = listOfNotNull(collectorLine?.text).joinToString(" ").uppercase()
        val setCode = codePattern.findAll(nearbyText)
            .map { it.value }
            .firstOrNull { candidate ->
                candidate !in ignoredCodes && !candidate.all(Char::isDigit) &&
                    !languagePattern.matches(candidate)
            }
            ?: codePattern.findAll(fullText.uppercase())
                .map { it.value }
                .firstOrNull { it !in ignoredCodes && !it.all(Char::isDigit) && !languagePattern.matches(it) }
                .orEmpty()

        val languageMarker = languagePattern.find(fullText.uppercase())?.value ?: "EN"
        val language = when (languageMarker) {
            "JP", "JA" -> "ja"
            else -> languageMarker.lowercase()
        }

        val title = lines
            .filter { line ->
                val box = line.boundingBox
                box != null && imageHeight > 0 && box.top in (imageHeight * 0.12).toInt()..(imageHeight * 0.38).toInt()
            }
            .filterNot { fractionPattern.containsMatchIn(it.text) }
            .minByOrNull { it.boundingBox?.top ?: Int.MAX_VALUE }
            ?.text
            ?.trim()
            .orEmpty()

        return OcrCandidate(title, setCode, collector, language)
    }
}
