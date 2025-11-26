package dev.kamikaze.mivdating.data.filtering

import dev.kamikaze.mivdating.data.storage.SearchResult

/**
 * Конфигурация фильтра релевантности
 */
data class FilterConfig(
    /**
     * Минимальный порог cosine similarity (от 0.0 до 1.0)
     * Результаты с score ниже этого порога будут отфильтрованы
     */
    val minScoreThreshold: Double = 0.5,

    /**
     * Использовать ли reranking на основе длины контента
     * Более длинные чанки получают небольшой буст
     */
    val useLengthBoost: Boolean = false,

    /**
     * Максимальное количество результатов после фильтрации
     */
    val maxResults: Int = 5
)

/**
 * Фильтр релевантности для результатов поиска RAG
 *
 * Применяет фильтрацию по порогу похожести и опционально reranking
 */
class RelevanceFilter(private val config: FilterConfig = FilterConfig()) {

    /**
     * Фильтрует результаты поиска по порогу релевантности
     *
     * @param results Исходные результаты поиска
     * @return Отфильтрованные и переранжированные результаты
     */
    fun filter(results: List<SearchResult>): FilteredResults {
        val originalCount = results.size

        // 1. Фильтрация по порогу
        val filtered = results.filter { it.score >= config.minScoreThreshold }

        // 2. Применение reranking, если включено
        val reranked = if (config.useLengthBoost) {
            applyLengthBoost(filtered)
        } else {
            filtered
        }

        // 3. Ограничение количества результатов
        val final = reranked.take(config.maxResults)

        return FilteredResults(
            results = final,
            originalCount = originalCount,
            filteredCount = filtered.size,
            finalCount = final.size,
            minScore = final.minOfOrNull { it.score },
            maxScore = final.maxOfOrNull { it.score },
            avgScore = if (final.isNotEmpty()) final.map { it.score }.average() else 0.0,
            appliedThreshold = config.minScoreThreshold
        )
    }

    /**
     * Применяет reranking на основе длины контента
     * Более длинные чанки (в пределах разумного) получают небольшой буст
     */
    private fun applyLengthBoost(results: List<SearchResult>): List<SearchResult> {
        if (results.isEmpty()) return results

        // Оптимальная длина чанка (примерно)
        val optimalLength = 500.0

        return results
            .map { result ->
                val lengthRatio = result.chunk.content.length / optimalLength
                // Буст от 0.95 до 1.05 в зависимости от близости к оптимальной длине
                val lengthFactor = when {
                    lengthRatio < 0.5 -> 0.95  // Слишком короткий
                    lengthRatio > 2.0 -> 0.95  // Слишком длинный
                    lengthRatio in 0.8..1.2 -> 1.05  // Близко к оптимальной длине
                    else -> 1.0
                }

                result.copy(score = result.score * lengthFactor)
            }
            .sortedByDescending { it.score }
    }

    /**
     * Анализирует качество результатов поиска
     */
    fun analyzeQuality(results: List<SearchResult>): QualityMetrics {
        if (results.isEmpty()) {
            return QualityMetrics(
                totalResults = 0,
                relevantResults = 0,
                irrelevantResults = 0,
                averageRelevantScore = 0.0,
                averageIrrelevantScore = 0.0,
                scoreDistribution = emptyMap()
            )
        }

        val relevant = results.filter { it.score >= config.minScoreThreshold }
        val irrelevant = results.filter { it.score < config.minScoreThreshold }

        // Распределение по диапазонам score
        val distribution = results.groupingBy {
            when {
                it.score >= 0.9 -> "Отлично (0.9-1.0)"
                it.score >= 0.7 -> "Хорошо (0.7-0.9)"
                it.score >= 0.5 -> "Средне (0.5-0.7)"
                it.score >= 0.3 -> "Плохо (0.3-0.5)"
                else -> "Очень плохо (<0.3)"
            }
        }.eachCount()

        return QualityMetrics(
            totalResults = results.size,
            relevantResults = relevant.size,
            irrelevantResults = irrelevant.size,
            averageRelevantScore = relevant.map { it.score }.average().takeIf { relevant.isNotEmpty() } ?: 0.0,
            averageIrrelevantScore = irrelevant.map { it.score }.average().takeIf { irrelevant.isNotEmpty() } ?: 0.0,
            scoreDistribution = distribution
        )
    }
}

/**
 * Результаты фильтрации
 */
data class FilteredResults(
    val results: List<SearchResult>,
    val originalCount: Int,
    val filteredCount: Int,
    val finalCount: Int,
    val minScore: Double?,
    val maxScore: Double?,
    val avgScore: Double,
    val appliedThreshold: Double
)

/**
 * Метрики качества результатов
 */
data class QualityMetrics(
    val totalResults: Int,
    val relevantResults: Int,
    val irrelevantResults: Int,
    val averageRelevantScore: Double,
    val averageIrrelevantScore: Double,
    val scoreDistribution: Map<String, Int>
)

/**
 * Расширение для SearchResult, позволяющее копировать с новым score
 */
fun SearchResult.copy(score: Double): SearchResult {
    return SearchResult(
        chunk = this.chunk,
        score = score,
        documentTitle = this.documentTitle
    )
}
