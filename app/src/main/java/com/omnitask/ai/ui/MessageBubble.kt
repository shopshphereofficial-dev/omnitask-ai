package com.omnitask.ai.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.ChatMessage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(msg: ChatMessage, enabled: Boolean, onRun: (ChatMessage) -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isUser = msg.role == "user"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (msg.content.isBlank() && msg.actionsJson == null) {
                Text("•••", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    text = msg.content,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (msg.content.isNotBlank()) {
                                clipboard.setText(AnnotatedString(msg.content))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                )
            }
            if (msg.actionsJson != null && !msg.executed) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onRun(msg) }, enabled = enabled) {
                    Text("Run actions")
                }
            }
            msg.results.forEach { r ->
                Spacer(Modifier.height(4.dp))
                if (r.startsWith("PHOTO:")) {
                    PhotoResult(r.removePrefix("PHOTO:"), isUser)
                } else {
                    Text(
                        text = "- $r",
                        fontSize = 12.sp,
                        color = if (isUser) Color(0xCCFFFFFF)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoResult(uriStr: String, isUser: Boolean) {
    val ctx = LocalContext.current
    val bmp = remember(uriStr) {
        try {
            ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Gallery photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Text(
            "Could not load photo",
            fontSize = 12.sp,
            color = if (isUser) Color(0xCCFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
