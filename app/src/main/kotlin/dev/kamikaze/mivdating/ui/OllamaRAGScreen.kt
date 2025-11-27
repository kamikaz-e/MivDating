package dev.kamikaze.mivdating.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamikaze.mivdating.RAGViewModel
import dev.kamikaze.mivdating.data.filtering.FilteredResults
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.network.ApiResponse
import dev.kamikaze.mivdating.data.storage.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OllamaRAGScreen(
    modifier: Modifier = Modifier,
    viewModel: RAGViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📚 RAG Document Index") },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearIndex() },
                        enabled = !uiState.isIndexing
                    ) {
                        Icon(Icons.Default.Delete, "Очистить индекс")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Статус подключения
            item {
                ConnectionStatus(isConnected = uiState.ollamaAvailable)
            }

            // Статистика индекса
            item {
                IndexStats(
                    documentsCount = uiState.documentsCount,
                    chunksCount = uiState.chunksCount
                )
            }

            // Список документов
            if (uiState.documents.isNotEmpty()) {
                item {
                    DocumentsList(documents = uiState.documents)
                }
            }

            // Кнопка индексации
            item {
                IndexingSection(
                    isIndexing = uiState.isIndexing,
                    progress = uiState.progress,
                    progressPercent = uiState.progressPercent,
                    onIndexClick = { viewModel.indexBooks() }
                )
            }

            // RAG Question Section
            if (uiState.chunksCount > 0) {
                item {
                    RagQuestionSection(
                        question = uiState.ragQuestion,
                        onQuestionChange = { viewModel.updateRagQuestion(it) },
                        onAsk = { viewModel.askQuestionWithRAG() },
                        isGenerating = uiState.isGenerating,
                        isEnabled = uiState.chunksCount > 0
                    )
                }
            }

            // Ответ LLM с RAG
            uiState.ragAnswer?.let { answer ->
                item {
                    RagAnswerSection(
                        answer = answer,
                        usedChunks = uiState.usedChunks,
                        onClear = { viewModel.clearRagResults() },
                        onSourceClick = { source -> viewModel.showSourceDialog(source) }
                    )
                }
            }

            // Поиск
            item {
                SearchSection(
                    query = viewModel.searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = { viewModel.search() },
                    isSearching = uiState.isSearching,
                    isEnabled = uiState.chunksCount > 0
                )
            }

            // Настройки фильтра
            if (uiState.chunksCount > 0) {
                item {
                    FilterSettingsSection(
                        threshold = uiState.filterThreshold,
                        onThresholdChange = { viewModel.updateFilterThreshold(it) },
                        useFilter = uiState.useFilter,
                        onToggleFilter = { viewModel.toggleFilter() },
                        useLengthBoost = uiState.useLengthBoost,
                        onToggleLengthBoost = { viewModel.toggleLengthBoost() },
                        comparisonMode = uiState.comparisonMode,
                        onToggleComparison = { viewModel.toggleComparisonMode() }
                    )
                }
            }

            // Ошибки
            uiState.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "❌ $error",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Результаты в режиме сравнения
            uiState.filteredResults?.let { filtered ->
                if (uiState.comparisonMode) {
                    item {
                        ComparisonResultsSection(
                            rawResults = uiState.searchResults,
                            filteredResults = filtered
                        )
                    }
                }
                // Результаты с фильтром
                else {
                    item {
                        FilteredResultsSection(filteredResults = filtered)
                    }
                }
            }

            // Результаты без фильтра
            if (uiState.searchResults.isNotEmpty() && uiState.filteredResults == null) {
                item {
                    Text(
                        "Результаты поиска (без фильтра):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(uiState.searchResults) { result ->
                    SearchResultCard(result = result)
                }
            }
        }

        // Диалог с источником (вне LazyColumn)
        uiState.selectedSource?.let { source ->
            if (uiState.showSourceDialog) {
                SourceDialog(
                    source = source,
                    onDismiss = { viewModel.closeSourceDialog() }
                )
            }
        }
    }
}

@Composable
fun ConnectionStatus(isConnected: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isConnected) "🟢 Ollama подключен" else "🔴 Ollama недоступен",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun IndexStats(documentsCount: Int, chunksCount: Int) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$documentsCount",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "документов",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$chunksCount",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "чанков",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun DocumentsList(documents: List<Document>) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "📚 Проиндексированные книги:",
                style = MaterialTheme.typography.titleMedium
            )

            documents.forEach { document ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun IndexingSection(
    isIndexing: Boolean,
    progress: String,
    progressPercent: Float,
    onIndexClick: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Индексация документов",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onIndexClick,
                enabled = !isIndexing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isIndexing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isIndexing) "Индексация..." else "▶️ Индексировать книги")
            }

            if (progress.isNotEmpty()) {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isIndexing && progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    isEnabled: Boolean
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Семантический поиск",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите поисковый запрос...") },
                enabled = isEnabled && !isSearching,
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = onSearch,
                        enabled = isEnabled && query.isNotBlank() && !isSearching
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, "Поиск")
                        }
                    }
                }
            )

            if (!isEnabled) {
                Text(
                    "Сначала проиндексируйте документы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun RagQuestionSection(
    question: String,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    isGenerating: Boolean,
    isEnabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "🤖 Вопрос с RAG",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Задайте вопрос по проиндексированным документам. Система найдет релевантные чанки и отправит их в LLM для формирования ответа.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Например: Кто главный герой книги?") },
                enabled = isEnabled && !isGenerating,
                minLines = 2,
                maxLines = 4
            )

            Button(
                onClick = onAsk,
                enabled = isEnabled && question.isNotBlank() && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isGenerating) "Генерация ответа..." else "🚀 Задать вопрос с RAG")
            }

            if (!isEnabled) {
                Text(
                    "Сначала проиндексируйте документы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// Функция для создания кликабельного текста с источниками
@Composable
fun ClickableSourceText(
    text: String,
    sources: List<SearchResult>,
    onSourceClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Регулярное выражение для поиска [Источник N]
    val sourcePattern = Regex("""\[Источник (\d+)\]""")

    val annotatedString = remember(text, sources, primaryColor) {
        buildAnnotatedString {
            var lastIndex = 0

            sourcePattern.findAll(text).forEach { matchResult ->
                // Добавляем текст до совпадения
                append(text.substring(lastIndex, matchResult.range.first))

                // Извлекаем номер источника
                val sourceNumber = matchResult.groupValues[1].toIntOrNull() ?: 1
                val sourceIndex = sourceNumber - 1

                // Добавляем кликабельный источник
                if (sourceIndex in sources.indices) {
                    pushStringAnnotation(
                        tag = "SOURCE",
                        annotation = sourceIndex.toString()
                    )
                    withStyle(
                        style = SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(matchResult.value)
                    }
                    pop()
                } else {
                    append(matchResult.value)
                }

                lastIndex = matchResult.range.last + 1
            }

            // Добавляем оставшийся текст
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "SOURCE",
                start = 0,
                end = annotatedString.length
            ).firstOrNull { annotation ->
                offset in annotation.start..annotation.end
            }?.let { annotation ->
                val sourceIndex = annotation.item.toIntOrNull() ?: 0
                if (sourceIndex >= 0 && sourceIndex < sources.size) {
                    onSourceClick(sources[sourceIndex])
                }
            }
        }
    )
}

@Composable
fun RagAnswerSection(
    answer: ApiResponse,
    usedChunks: List<SearchResult>,
    onClear: () -> Unit,
    onSourceClick: (SearchResult) -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "💬 Ответ LLM с источниками",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, "Очистить")
                }
            }

            // Ответ с кликабельными источниками
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                ClickableSourceText(
                    text = answer.text,
                    sources = usedChunks,
                    onSourceClick = onSourceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            // Проверка наличия источников в ответе
            val hasSourceReferences = answer.text.contains(Regex("\\[Источник \\d+"))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasSourceReferences)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (hasSourceReferences)
                            "✓ Ответ содержит ссылки на источники"
                        else
                            "⚠ Ответ БЕЗ ссылок на источники (возможна галлюцинация)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Статистика токенов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📊 Токены: ${answer.tokens.totalTokens}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Input: ${answer.tokens.inputTokens} | Output: ${answer.tokens.outputTokens}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Использованные чанки
            Text(
                "📚 Использовано чанков: ${usedChunks.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            usedChunks.forEach { chunk ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "📄 ${chunk.documentTitle}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%.3f".format(chunk.score),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            chunk.chunk.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: SearchResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📄 ${result.documentTitle}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "%.4f".format(result.score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = result.chunk.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FilterSettingsSection(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    useFilter: Boolean,
    onToggleFilter: () -> Unit,
    useLengthBoost: Boolean,
    onToggleLengthBoost: () -> Unit,
    comparisonMode: Boolean,
    onToggleComparison: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "⚙️ Настройки фильтра релевантности",
                style = MaterialTheme.typography.titleMedium
            )

            // Порог фильтрации
            Column {
                Text(
                    "Минимальный порог похожести: %.2f".format(threshold),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = 0f..1f,
                    steps = 19
                )
            }

            HorizontalDivider()

            // Опции
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useFilter,
                    onCheckedChange = { onToggleFilter() },
                    enabled = !comparisonMode
                )
                Text(
                    "Использовать фильтр",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useLengthBoost,
                    onCheckedChange = { onToggleLengthBoost() }
                )
                Text(
                    "Reranking по длине контента",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = comparisonMode,
                    onCheckedChange = { onToggleComparison() }
                )
                Text(
                    "Режим сравнения (с фильтром и без)",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FilteredResultsSection(filteredResults: FilteredResults) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "📊 Результаты с фильтром",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Исходных: ${filteredResults.originalCount}", style = MaterialTheme.typography.bodySmall)
                    Text("После фильтра: ${filteredResults.finalCount}", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("Средний score: %.3f".format(filteredResults.avgScore), style = MaterialTheme.typography.bodySmall)
                    Text("Порог: %.2f".format(filteredResults.appliedThreshold), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    filteredResults.results.forEach { result ->
        SearchResultCard(result = result)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ComparisonResultsSection(
    rawResults: List<SearchResult>,
    filteredResults: FilteredResults
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "🔬 Сравнение результатов",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("БЕЗ ФИЛЬТРА", style = MaterialTheme.typography.labelLarge)
                    Text("${rawResults.size}", style = MaterialTheme.typography.headlineMedium)
                    Text("результатов", style = MaterialTheme.typography.bodySmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("С ФИЛЬТРОМ", style = MaterialTheme.typography.labelLarge)
                    Text("${filteredResults.finalCount}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("результатов", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            Text(
                "Статистика фильтрации:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text("• Отфильтровано: ${filteredResults.originalCount - filteredResults.finalCount} результатов")
            Text("• Средний score (с фильтром): %.3f".format(filteredResults.avgScore))
            Text("• Примененный порог: %.2f".format(filteredResults.appliedThreshold))
        }
    }

    Spacer(Modifier.height(16.dp))

    // Результаты без фильтра
    Text(
        "Без фильтра (${rawResults.size} результатов):",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    rawResults.forEach { result ->
        SearchResultCard(result = result)
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(16.dp))

    // Результаты с фильтром
    Text(
        "С фильтром (${filteredResults.finalCount} результатов):",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    filteredResults.results.forEach { result ->
        SearchResultCard(result = result)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SourceDialog(
    source: SearchResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Источник") },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Документ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = source.documentTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Релевантность",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = String.format("%.2f%%", source.score * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Содержимое чанка",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = source.chunk.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}