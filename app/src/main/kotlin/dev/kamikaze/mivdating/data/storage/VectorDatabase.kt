package dev.kamikaze.mivdating.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import dev.kamikaze.mivdating.data.models.ChunkEmbedding
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.models.DocumentType
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

class VectorDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "vector_index.db"
        private const val DATABASE_VERSION = 1

        // Таблицы
        private const val TABLE_DOCUMENTS = "documents"
        private const val TABLE_EMBEDDINGS = "embeddings"

        // Колонки documents
        private const val COL_DOC_ID = "id"
        private const val COL_DOC_TITLE = "title"
        private const val COL_DOC_SOURCE = "source"
        private const val COL_DOC_TYPE = "type"

        // Колонки embeddings
        private const val COL_EMB_ID = "id"
        private const val COL_EMB_CHUNK_ID = "chunk_id"
        private const val COL_EMB_DOC_ID = "document_id"
        private const val COL_EMB_CONTENT = "content"
        private const val COL_EMB_VECTOR = "vector"  // JSON-строка
    }

    private val json = Json { prettyPrint = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_DOCUMENTS (
                $COL_DOC_ID TEXT PRIMARY KEY,
                $COL_DOC_TITLE TEXT NOT NULL,
                $COL_DOC_SOURCE TEXT NOT NULL,
                $COL_DOC_TYPE TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_EMBEDDINGS (
                $COL_EMB_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMB_CHUNK_ID TEXT UNIQUE NOT NULL,
                $COL_EMB_DOC_ID TEXT NOT NULL,
                $COL_EMB_CONTENT TEXT NOT NULL,
                $COL_EMB_VECTOR TEXT NOT NULL,
                FOREIGN KEY ($COL_EMB_DOC_ID) REFERENCES $TABLE_DOCUMENTS($COL_DOC_ID)
            )
        """)

        // Индекс для быстрого поиска по документу
        db.execSQL("CREATE INDEX idx_doc_id ON $TABLE_EMBEDDINGS($COL_EMB_DOC_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMBEDDINGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
        onCreate(db)
    }

    // === Операции с документами ===

    fun insertDocument(document: Document) {
        writableDatabase.insertWithOnConflict(
            TABLE_DOCUMENTS,
            null,
            ContentValues().apply {
                put(COL_DOC_ID, document.id)
                put(COL_DOC_TITLE, document.title)
                put(COL_DOC_SOURCE, document.source)
                put(COL_DOC_TYPE, document.type.name)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAllDocuments(): List<Document> {
        val documents = mutableListOf<Document>()
        readableDatabase.query(
            TABLE_DOCUMENTS,
            null, null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                documents.add(
                    Document(
                        id = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_TITLE)),
                        source = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_SOURCE)),
                        type = DocumentType.valueOf(
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_TYPE))
                        )
                    )
                )
            }
        }
        return documents.distinctBy { it.title }
    }

    // === Операции с эмбеддингами ===

    fun insertEmbedding(embedding: ChunkEmbedding) {
        val vectorJson = json.encodeToString(embedding.embedding)
        
        writableDatabase.insertWithOnConflict(
            TABLE_EMBEDDINGS,
            null,
            ContentValues().apply {
                put(COL_EMB_CHUNK_ID, embedding.chunkId)
                put(COL_EMB_DOC_ID, embedding.documentId)
                put(COL_EMB_CONTENT, embedding.content)
                put(COL_EMB_VECTOR, vectorJson)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertEmbeddings(embeddings: List<ChunkEmbedding>) {
        writableDatabase.transaction {
            try {
                embeddings.forEach { insertEmbedding(it) }
            } finally {
            }
        }
    }

    fun getAllEmbeddings(): List<ChunkEmbedding> {
        val embeddings = mutableListOf<ChunkEmbedding>()
        readableDatabase.query(
            TABLE_EMBEDDINGS,
            null, null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val vectorJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMB_VECTOR))
                val vector: List<Double> = json.decodeFromString(vectorJson)
                
                embeddings.add(
                    ChunkEmbedding(
                        chunkId = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMB_CHUNK_ID)),
                        documentId = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMB_DOC_ID)),
                        content = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMB_CONTENT)),
                        embedding = vector
                    )
                )
            }
        }
        return embeddings
    }

    fun getEmbeddingsCount(): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_EMBEDDINGS",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun getDocumentsCount(): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_DOCUMENTS",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    // === Поиск по сходству ===

    fun searchSimilar(queryEmbedding: List<Double>, topK: Int = 5): List<SearchResult> {
        val allEmbeddings = getAllEmbeddings()

        // Создаем Map documentId -> title для быстрого поиска
        val documentTitles = getAllDocuments().associateBy({ it.id }, { it.title })

        return allEmbeddings
            .map { chunk ->
                SearchResult(
                    chunk = chunk,
                    score = cosineSimilarity(queryEmbedding, chunk.embedding),
                    documentTitle = documentTitles[chunk.documentId] ?: "Unknown"
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM $TABLE_EMBEDDINGS")
        writableDatabase.execSQL("DELETE FROM $TABLE_DOCUMENTS")
    }
}

data class SearchResult(
    val chunk: ChunkEmbedding,
    val score: Double,
    val documentTitle: String
)