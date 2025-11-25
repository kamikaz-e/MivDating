package dev.kamikaze.mivdating.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamikaze.mivdating.ChatViewModel
import dev.kamikaze.mivdating.data.models.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAG vs NO RAG Comparison") },
                actions = {
                    IconButton(onClick = { viewModel.clearAll() }) {
                        Icon(Icons.Default.Delete, "Очистить всё")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("NO RAG") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("RAG") }
                )
            }

            when (selectedTab) {
                0 -> NoRagScreen(viewModel = viewModel, uiState = uiState)
                1 -> RagScreen(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}

@Composable
fun NoRagScreen(
    viewModel: ChatViewModel,
    uiState: dev.kamikaze.mivdating.ChatUiState
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Список сообщений
        val listState = rememberLazyListState()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.noRagMessages) { message ->
                ChatMessageItem(message = message)
            }

            if (uiState.isNoRagLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Автоскролл вниз
        LaunchedEffect(uiState.noRagMessages.size) {
            if (uiState.noRagMessages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.noRagMessages.size - 1)
            }
        }

        // Ошибки
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearNoRagChat() }) {
                Icon(Icons.Default.Clear, "Очистить чат")
            }

            OutlinedTextField(
                value = viewModel.noRagInput,
                onValueChange = { viewModel.updateNoRagInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Задайте вопрос...") },
                enabled = !uiState.isNoRagLoading,
                maxLines = 3
            )

            IconButton(
                onClick = { viewModel.sendNoRagMessage() },
                enabled = viewModel.noRagInput.isNotBlank() && !uiState.isNoRagLoading
            ) {
                Icon(Icons.Default.Send, "Отправить")
            }
        }
    }
}

@Composable
fun RagScreen(
    viewModel: ChatViewModel,
    uiState: dev.kamikaze.mivdating.ChatUiState
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Информация о документах
        if (uiState.documentsCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📚 Проиндексировано: ${uiState.documentsCount} документов, ${uiState.chunksCount} чанков",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.documents.isNotEmpty()) {
                        Text(
                            "Книги: ${uiState.documents.joinToString(", ") { it.title }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "⚠️ Документы не проиндексированы",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.indexBooks() },
                        enabled = !uiState.isIndexing
                    ) {
                        Text("Индексировать книги")
                    }

                    if (uiState.isIndexing) {
                        Text(
                            text = uiState.indexingProgress,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.indexingPercent > 0) {
                            LinearProgressIndicator(
                                progress = { uiState.indexingPercent },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Список сообщений
        val listState = rememberLazyListState()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.ragMessages) { message ->
                ChatMessageItem(message = message)
            }

            if (uiState.isRagLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Автоскролл вниз
        LaunchedEffect(uiState.ragMessages.size) {
            if (uiState.ragMessages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.ragMessages.size - 1)
            }
        }

        // Ошибки
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearRagChat() }) {
                Icon(Icons.Default.Clear, "Очистить чат")
            }

            OutlinedTextField(
                value = viewModel.ragInput,
                onValueChange = { viewModel.updateRagInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Задайте вопрос...") },
                enabled = !uiState.isRagLoading && uiState.chunksCount > 0,
                maxLines = 3
            )

            IconButton(
                onClick = { viewModel.sendRagMessage() },
                enabled = viewModel.ragInput.isNotBlank() && !uiState.isRagLoading && uiState.chunksCount > 0
            ) {
                Icon(Icons.Default.Send, "Отправить")
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (message.isUser) "Вы" else "Ассистент",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
