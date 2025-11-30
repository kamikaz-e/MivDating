package dev.kamikaze.mivdating.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.kamikaze.mivdating.data.storage.SearchResult

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