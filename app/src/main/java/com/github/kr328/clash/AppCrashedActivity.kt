package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.log.SystemLogcat
import com.github.kr328.clash.log.CrashReport
import com.github.kr328.clash.log.ReportScrubber
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.theme.DuckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Экран после падения приложения. Нужен ради одного: отдать человеку запись падения,
 * которую иначе достать нечем.
 */
class AppCrashedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DuckerTheme {
                Surface(Modifier.fillMaxSize()) {
                    val logs by crashLogs()
                    val scroll = rememberScrollState()

                    Column(
                        modifier = Modifier.fillMaxSize().screenInsets().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_crashed_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.app_crashed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Button(onClick = { copy(logs) }, enabled = logs.isNotEmpty()) {
                            Text(stringResource(R.string.logs_copy))
                        }

                        Text(
                            text = logs,
                            modifier = Modifier.fillMaxWidth().verticalScroll(scroll, overscrollEffect = overscrollFor(scroll)),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun crashLogs() = produceState("") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val scrubber = ReportScrubber.from(this@AppCrashedActivity)
                CrashReport.capture(filesDir, SystemLogcat.dumpCrash(), scrubber::scrub)
            }.getOrDefault(getString(R.string.crash_report_unavailable))
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("ducker", text))
    }
}
