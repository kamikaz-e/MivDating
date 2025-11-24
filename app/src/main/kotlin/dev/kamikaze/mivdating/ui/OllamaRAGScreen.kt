package dev.kamikaze.mivdating.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamikaze.mivdating.RAGViewModel
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

            // Кнопка индексации
            item {
                IndexingSection(
                    isIndexing = uiState.isIndexing,
                    progress = uiState.progress,
                    progressPercent = uiState.progressPercent,
                    onIndexClick = { viewModel.indexBooks() }
                )
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

            // Результаты поиска
            if (uiState.searchResults.isNotEmpty()) {
                item {
                    Text(
                        "Результаты поиска:",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(uiState.searchResults) { result ->
                    SearchResultCard(result = result)
                }
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📄 Chunk: ${result.chunk.chunkId.take(8)}...",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Score: ${"%.4f".format(result.score)}",
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