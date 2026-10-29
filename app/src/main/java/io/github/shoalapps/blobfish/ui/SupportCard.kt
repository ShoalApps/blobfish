package io.github.shoalapps.blobfish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import io.github.shoalapps.blobfish.LocalisedIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.shoalapps.blobfish.LocalisedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Supporting Shoal, at the top of Settings where it's seen: a bright card in the app's own
 * colours (primary into tertiary), with a heart and a button to Liberapay.
 */
@Composable
fun SupportCard(onDonate: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp), color = c.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.clickable(onClickLabel = "Donate on Liberapay", onClick = onDonate)
                .background(Brush.linearGradient(listOf(c.primary, c.tertiary))).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(36.dp), tint = c.onPrimary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Support Shoal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.onPrimary)
                Text("Free, open source and ad-free. Donations on Liberapay keep it going.", style = MaterialTheme.typography.bodyMedium, color = c.onPrimary.copy(alpha = .9f))
                Button(
                    onClick = onDonate, modifier = Modifier.padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.onPrimary, contentColor = c.primary),
                ) { Text("Donate", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
