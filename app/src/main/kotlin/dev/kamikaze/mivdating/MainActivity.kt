package dev.kamikaze.mivdating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import dev.kamikaze.mivdating.ui.ChatScreen
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
                ChatScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
