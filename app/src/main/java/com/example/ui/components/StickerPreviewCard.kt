package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.StickerItem
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.WhatsAppGreen
import java.io.File

@Composable
fun StickerPreviewCard(
    sticker: StickerItem,
    imageLoader: ImageLoader,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F1117))
            .border(1.dp, Color(0xFF232738), RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("sticker_preview_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Name and Delete action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sticker.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { onDelete(sticker.id) },
                    modifier = Modifier.size(36.dp).testTag("delete_sticker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete sticker",
                        tint = Color(0xFFFF5252)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Sticker / Animated GIF display
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1E2230), Color(0xFF0A0C12))
                        )
                    )
                    .border(1.dp, Color(0xFF2B3145), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(sticker.filePath))
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Active sticker preview: ${sticker.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    loading = {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = WhatsAppGreen,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spec Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TagPill(
                    text = sticker.typeLabel,
                    backgroundColor = if (sticker.isAnimated) Color(0xFF2E1534) else Color(0xFF122820),
                    textColor = if (sticker.isAnimated) InstagramAccent else WhatsAppGreen
                )

                TagPill(
                    text = "512×512",
                    backgroundColor = Color(0xFF161A26),
                    textColor = NeonCyan
                )

                TagPill(
                    text = sticker.formattedSize,
                    backgroundColor = Color(0xFF161A26),
                    textColor = Color(0xFFA5B0C7)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Readiness status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF131824))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Ready check",
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ready for WhatsApp Pack & Instagram Stories",
                    color = Color(0xFFD2D8E6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TagPill(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(0.5.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
