package br.com.pilovieira.launcher.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.pilovieira.launcher.R

class ClipboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClipboardScreen(onBackClick = { finish() })
        }
    }
}

@Composable
private fun ClipboardScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(ClipboardHistoryStore.getAll(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.back),
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.clipboard),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (entries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.clear),
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        ClipboardHistoryStore.clear(context)
                        entries = emptyList()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.clipboard_empty),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = entries, key = { it.id }) { entry ->
                    ClipboardListItem(
                        entry = entry,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(entry.text, entry.text))
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            ClipboardHistoryStore.remove(context, entry.id)
                            entries = entries.filterNot { it.id == entry.id }
                        }
                    )
                    HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ClipboardListItem(
    entry: ClipboardEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = entry.text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 3,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Text(
            text = "✕",
            color = Color.Gray,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable(onClick = onDelete)
                .padding(8.dp)
        )
    }
}
