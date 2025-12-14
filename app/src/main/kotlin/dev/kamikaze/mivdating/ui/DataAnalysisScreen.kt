package dev.kamikaze.mivdating.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kamikaze.mivdating.DataAnalysisViewModel
import dev.kamikaze.mivdating.data.analytics.AnalysisResult
import dev.kamikaze.mivdating.data.analytics.DataFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAnalysisScreen(
    viewModel: DataAnalysisViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Локальный анализ данных") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Статус Ollama и выбор файла (компактно вверху)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OllamaStatusCard(isAvailable = uiState.ollamaAvailable)

                // Выбор файла (компактный)
                if (uiState.availableFiles.isNotEmpty()) {
                    CompactFileSelector(
                        files = uiState.availableFiles,
                        selectedFile = uiState.selectedFile,
                        onFileSelected = { viewModel.selectFile(it) }
                    )
                }

                // Предложенные вопросы (компактно)
                if (uiState.selectedFile != null && uiState.suggestedQuestions.isNotEmpty()) {
                    SuggestedQuestions(
                        questions = uiState.suggestedQuestions,
                        onQuestionClick = { viewModel.updateQuestion(it) }
                    )
                }

                // Ошибки
                uiState.error?.let { error ->
                    ErrorCard(error = error, onDismiss = { viewModel.clearError() })
                }
            }

            // Чат с историей (занимает оставшееся место)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.analysisHistory.isEmpty()) {
                    // Подсказка если нет истории
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Выберите файл и задайте вопрос",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // История в виде чата
                    ChatHistory(results = uiState.analysisHistory)
                }
            }

            // Поле ввода внизу (зафиксировано)
            if (uiState.selectedFile != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    ChatInput(
                        question = uiState.currentQuestion,
                        onQuestionChange = { viewModel.updateQuestion(it) },
                        onSend = { viewModel.analyzeData() },
                        isAnalyzing = uiState.isAnalyzing,
                        enabled = uiState.ollamaAvailable
                    )
                }
            }
        }
    }
}

@Composable
fun OllamaStatusCard(isAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isAvailable) "✓ Ollama подключен" else "✗ Ollama не доступен",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactFileSelector(
    files: List<DataFile>,
    selectedFile: DataFile?,
    onFileSelected: (DataFile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Источник данных:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedFile?.name ?: "Выберите файл",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    files.forEach { file ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(file.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        file.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onFileSelected(file)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SuggestedQuestions(
    questions: List<String>,
    onQuestionClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Заголовок с кнопкой раскрытия/скрытия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Примеры вопросов",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Скрыть" else "Показать"
                    )
                }
            }

            // Список вопросов (показывается только если expanded = true)
            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    questions.forEach { question ->
                        SuggestionChip(
                            onClick = { onQuestionClick(question) },
                            label = { Text(question, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    question: String,
    onQuestionChange: (String) -> Unit,
    onSend: () -> Unit,
    isAnalyzing: Boolean,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Задайте вопрос о данных...") },
            enabled = enabled && !isAnalyzing,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )

        val canSend = enabled && !isAnalyzing && question.isNotBlank()

        FloatingActionButton(
            onClick = {
                if (canSend) {
                    onSend()
                }
            },
            modifier = Modifier.size(56.dp),
            containerColor = if (canSend)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (canSend)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
fun ErrorCard(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ChatHistory(results: List<AnalysisResult>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        reverseLayout = false
    ) {
        items(results) { result ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Сообщение пользователя
                UserMessageBubble(question = result.question)

                // Ответ AI
                AiMessageBubble(
                    answer = result.answer,
                    dataSource = result.dataSource,
                    success = result.success
                )
            }
        }

        // Отступ снизу
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun UserMessageBubble(question: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        ) {
            Text(
                text = question,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun AiMessageBubble(
    answer: String,
    dataSource: String,
    success: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (success)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (success)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )

                HorizontalDivider()

                Text(
                    text = "Источник: $dataSource",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (success)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}
