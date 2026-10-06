package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.theme.DuckerTheme

private const val RELEASES = "https://github.com/duckerapp/ducker/releases"

/** Приложение собрано или установлено не целиком: части файлов нет. */
class ApkBrokenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DuckerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().screenInsets().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = stringResource(R.string.apk_broken_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.apk_broken_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { open() }) {
                            Text(stringResource(R.string.apk_broken_open))
                        }
                    }
                }
            }
        }
    }

    private fun open() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))) }
    }
}
