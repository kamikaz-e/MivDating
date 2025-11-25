package dev.kamikaze.mivdating.data.parser

import android.content.Context
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.models.DocumentType
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class DocumentParser(private val context: Context) {

    /**
     * Парсит документ из assets
     */
    fun parseFromAssets(fileName: String): Pair<Document, String> {
        val type = when {
            fileName.endsWith(".html", ignoreCase = true) -> DocumentType.HTML
            fileName.endsWith(".txt", ignoreCase = true) -> DocumentType.TXT
            else -> throw IllegalArgumentException("Unsupported file type: $fileName")
        }

        val content = readAssetFile(fileName)
        val text = when (type) {
            DocumentType.HTML -> parseHtml(content)
            DocumentType.TXT -> content
        }

        val document = Document(
            id = UUID.randomUUID().toString(),
            title = fileName.substringBeforeLast("."),
            source = "assets/$fileName",
            type = type
        )

        return document to text
    }

    private fun readAssetFile(fileName: String): String {
        return context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun parseHtml(html: String): String {
        val doc = Jsoup.parse(html)
        // Убираем скрипты и стили
        doc.select("script, style, nav, footer, header").remove()
        // Получаем чистый текст
        return doc.body().text()
    }
}