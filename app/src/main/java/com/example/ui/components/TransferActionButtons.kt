package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.InstagramOrange
import com.example.ui.theme.InstagramPink
import com.example.ui.theme.InstagramPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.WhatsAppDarkGreen
import com.example.ui.theme.WhatsAppGreen

@Composable
fun TransferActionButtons(
    onAddToWhatsAppPack: () -> Unit,
    onAddToInstagramStory: () -> Unit,
    onDirectWhatsApp: () -> Unit,
    onDirectInstagram: () -> Unit,
    onTransferAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val instagramGradient = Brush.horizontalGradient(
        listOf(InstagramPurple, InstagramPink, InstagramOrange)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master 1-Tap Transfer All Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(WhatsAppGreen, NeonCyan, InstagramAccent)
                    )
                )
                .clickable(onClick = onTransferAll)
                .testTag("transfer_all_button")
                .padding(vertical = 16.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Transfer All",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Transfer to Both (WhatsApp & Insta)",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // WhatsApp Pack Addition Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WhatsAppGreen)
                .clickable(onClick = onAddToWhatsAppPack)
                .testTag("whatsapp_pack_button")
                .padding(vertical = 14.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddReaction,
                    contentDescription = "WhatsApp Pack",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Add to WhatsApp Sticker Pack",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Official sticker pack integration",
                        color = Color(0xFF0F3E1E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Instagram Stories Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(instagramGradient)
                .clickable(onClick = onAddToInstagramStory)
                .testTag("instagram_story_button")
                .padding(vertical = 14.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Instagram Stories",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Add to Instagram Story Sticker",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Interactive floating story sticker",
                        color = Color(0xFFFFD5E2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Secondary Quick Direct Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Direct WhatsApp Chat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131722))
                    .border(1.dp, Color(0xFF263047), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDirectWhatsApp)
                    .testTag("direct_whatsapp_button")
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "WhatsApp Chat",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WhatsApp Chat",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Direct Instagram Direct
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131722))
                    .border(1.dp, Color(0xFF263047), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDirectInstagram)
                    .testTag("direct_instagram_button")
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Instagram Direct",
                        tint = InstagramAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Insta Direct",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
