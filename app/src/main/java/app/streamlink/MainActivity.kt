package app.streamlink

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.streamlink.ui.StreamLinkApp
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.theme.StreamLinkTheme
import app.streamlink.util.CameraCapabilities

class MainActivity : ComponentActivity() {

    private val viewModel: StreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        CameraCapabilities.logCapabilities(this)
        setContent {
            StreamLinkTheme {
                StreamLinkApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == ACTION_STOP_STREAM) {
            viewModel.stopStream()
            return
        }

        val data = intent.dataString ?: return
        if (data.startsWith("moblin://", true) ||
            data.startsWith("streamlink://", true) ||
            data.startsWith("irlstream://", true)
        ) {
            viewModel.importDeepLink(data)
        }
    }

    companion object {
        const val ACTION_STOP_STREAM = "app.streamlink.action.STOP_STREAM"
    }
}
