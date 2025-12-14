package dev.kamikaze.mivdating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import dev.kamikaze.mivdating.ui.ChatScreen
import dev.kamikaze.mivdating.ui.DataAnalysisScreen
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(timber.log.Timber.DebugTree())
        }

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Чат с RAG", "Анализ данных")

                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                                icon = {
                                    Icon(
                                        imageVector = if (index == 0) Icons.Default.Chat else Icons.Default.Analytics,
                                        contentDescription = title
                                    )
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> ChatScreen(modifier = Modifier.fillMaxSize())
                        1 -> DataAnalysisScreen()
                    }
                }
            }
        }
    }
}
