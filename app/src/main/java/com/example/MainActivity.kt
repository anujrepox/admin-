package com.example

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.data.TransferHelper
import com.example.model.StickerItem
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.TransferTarget
import com.example.ui.components.StickerPackStrip
import com.example.ui.components.StickerPreviewCard
import com.example.ui.components.TransferActionButtons
import com.example.ui.components.TransferStatusDialog
import com.example.ui.components.UploadButton
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WhatsAppGreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()
                StickerDropScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

@Composable
fun StickerDropScreen(
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Configure Coil ImageLoader with animated GIF / WebP decoder support
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // Photo & Media picker
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                viewModel.onSingleMediaPicked(uris.first())
            } else {
                viewModel.onMultipleMediaPicked(uris)
            }
        }
    }

    // WhatsApp Sticker Pack Result Launcher
    val whatsAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.showDialog(
                title = "Added to WhatsApp!",
                message = "The sticker pack has been successfully added to your WhatsApp keyboard. Open any chat to use it!",
                isSuccess = true,
                targetApp = "WhatsApp"
            )
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            viewModel.showDialog(
                title = "WhatsApp Status",
                message = "WhatsApp processed the sticker pack request. Check WhatsApp Stickers in your chat keyboard.",
                isSuccess = true,
                targetApp = "WhatsApp"
            )
        }
    }

    // Handle snackbar messages
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Execute transfer actions
    fun executeTransfer(target: TransferTarget) {
        val sticker = uiState.selectedSticker
        when (target) {
            TransferTarget.WHATSAPP_PACK -> {
                try {
                    val intent = TransferHelper.createAddToWhatsAppIntent(
                        context = context,
                        packId = uiState.packInfo.id,
                        packName = uiState.packInfo.name
                    )
                    whatsAppLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    if (sticker != null) {
                        viewModel.showDialog(
                            title = "WhatsApp Not Detected",
                            message = "WhatsApp is not installed on this device or emulator. You can still share this sticker file directly via the Android Share menu.",
                            isSuccess = false,
                            targetApp = "WhatsApp"
                        )
                    } else {
                        Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    viewModel.showDialog(
                        title = "WhatsApp Transfer",
                        message = "Transfer note: ${e.localizedMessage ?: "Ensure WhatsApp is installed"}",
                        isSuccess = false
                    )
                }
            }

            TransferTarget.INSTAGRAM_STORY -> {
                if (sticker == null) return
                try {
                    val intent = TransferHelper.createInstagramStoryIntent(context, sticker)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    viewModel.showDialog(
                        title = "Instagram Not Detected",
                        message = "Instagram is not installed on this device. You can share this sticker directly with any messaging app.",
                        isSuccess = false,
                        targetApp = "Instagram"
                    )
                } catch (e: Exception) {
                    viewModel.showDialog(
                        title = "Instagram Transfer",
                        message = "Could not open Instagram Stories: ${e.localizedMessage}",
                        isSuccess = false
                    )
                }
            }

            TransferTarget.WHATSAPP_DIRECT -> {
                if (sticker == null) return
                try {
                    val intent = TransferHelper.createWhatsAppDirectShareIntent(context, sticker)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(TransferHelper.createGeneralShareIntent(context, sticker, "Send via WhatsApp"))
                }
            }

            TransferTarget.INSTAGRAM_DIRECT -> {
                if (sticker == null) return
                try {
                    val intent = TransferHelper.createInstagramDirectShareIntent(context, sticker)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(TransferHelper.createGeneralShareIntent(context, sticker, "Send via Instagram"))
                }
            }

            TransferTarget.ALL -> {
                // Perform WhatsApp Pack addition first
                executeTransfer(TransferTarget.WHATSAPP_PACK)
                Toast.makeText(context, "Transferring to WhatsApp Pack & readying Instagram!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-transfer trigger upon upload
    LaunchedEffect(uiState.pendingTransferTarget) {
        val target = uiState.pendingTransferTarget
        if (target != null) {
            viewModel.clearPendingTransfer()
            executeTransfer(target)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.packInfo.stickers.isEmpty()) {
                // Centered single UPLOAD button with app logo and powered by makeurfun footer
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 500.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        // App Logo
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "makeurfun logo",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Sticker",
                                color = TextPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Drop",
                                color = WhatsAppGreen,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Prominent centered UPLOAD button
                        UploadButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            isProcessing = uiState.isProcessing
                        )
                    }

                    // Powered by makeurfun pinned at the bottom
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Powered by ",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "makeurfun",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                // When stickers are uploaded: preview and transfer actions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top header with brand and upload more option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sticker",
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Drop",
                                color = WhatsAppGreen,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141824))
                                .border(1.dp, Color(0xFF263047), RoundedCornerShape(12.dp))
                                .clickable {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "+ Upload New",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active Sticker Preview Card
                    uiState.selectedSticker?.let { activeSticker ->
                        Spacer(modifier = Modifier.height(18.dp))

                        StickerPreviewCard(
                            sticker = activeSticker,
                            imageLoader = imageLoader,
                            onDelete = { viewModel.removeSticker(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Transfer Action Hub
                        TransferActionButtons(
                            onAddToWhatsAppPack = { executeTransfer(TransferTarget.WHATSAPP_PACK) },
                            onAddToInstagramStory = { executeTransfer(TransferTarget.INSTAGRAM_STORY) },
                            onDirectWhatsApp = { executeTransfer(TransferTarget.WHATSAPP_DIRECT) },
                            onDirectInstagram = { executeTransfer(TransferTarget.INSTAGRAM_DIRECT) },
                            onTransferAll = { executeTransfer(TransferTarget.ALL) }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Pack Carousel Strip
                    StickerPackStrip(
                        stickers = uiState.packInfo.stickers,
                        selectedStickerId = uiState.selectedSticker?.id,
                        imageLoader = imageLoader,
                        onSelectSticker = { viewModel.selectSticker(it) },
                        onAddMore = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearPack = { viewModel.clearPack() }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Powered by makeurfun at bottom of scrollable content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Powered by ",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "makeurfun",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Loading overlay
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF141824))
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = WhatsAppGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Optimizing GIF for WhatsApp & Insta...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Transfer Status Dialog
            uiState.dialogInfo?.let { dialogInfo ->
                val activeSticker = uiState.selectedSticker
                TransferStatusDialog(
                    info = dialogInfo,
                    onDismiss = { viewModel.dismissDialog() },
                    onShareFallback = if (activeSticker != null) {
                        {
                            viewModel.dismissDialog()
                            context.startActivity(
                                TransferHelper.createGeneralShareIntent(
                                    context = context,
                                    sticker = activeSticker,
                                    title = "Share ${activeSticker.name}"
                                )
                            )
                        }
                    } else null
                )
            }
        }
    }
}
