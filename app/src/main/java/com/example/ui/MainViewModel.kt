package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StickerManager
import com.example.data.TransferHelper
import com.example.model.StickerItem
import com.example.model.StickerPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransferTarget {
    WHATSAPP_PACK,
    WHATSAPP_DIRECT,
    INSTAGRAM_STORY,
    INSTAGRAM_DIRECT,
    ALL
}

data class TransferDialogInfo(
    val title: String,
    val message: String,
    val isSuccess: Boolean = true,
    val targetApp: String? = null
)

data class MainUiState(
    val packInfo: StickerPackInfo = StickerPackInfo(),
    val selectedSticker: StickerItem? = null,
    val isProcessing: Boolean = false,
    val autoTransfer: Boolean = true,
    val statusMessage: String? = null,
    val isWhatsAppInstalled: Boolean = false,
    val isInstagramInstalled: Boolean = false,
    val dialogInfo: TransferDialogInfo? = null,
    val pendingTransferTarget: TransferTarget? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val pack = StickerManager.getPackInfo(context)
            val waInstalled = TransferHelper.isWhatsAppInstalled(context)
            val igInstalled = TransferHelper.isInstagramInstalled(context)

            val currentSelected = _uiState.value.selectedSticker
            val validSelected = pack.stickers.find { it.id == currentSelected?.id }
                ?: pack.stickers.lastOrNull()

            _uiState.update {
                it.copy(
                    packInfo = pack,
                    selectedSticker = validSelected,
                    isWhatsAppInstalled = waInstalled,
                    isInstagramInstalled = igInstalled
                )
            }
        }
    }

    fun loadSampleStickers() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Loading sample stickers...") }
            StickerManager.seedSampleStickers(context)
            val pack = StickerManager.getPackInfo(context)
            _uiState.update {
                it.copy(
                    packInfo = pack,
                    selectedSticker = pack.stickers.firstOrNull(),
                    isProcessing = false,
                    statusMessage = "Loaded sample stickers! Ready to transfer."
                )
            }
        }
    }

    fun onSingleMediaPicked(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Processing GIF/sticker...") }
            try {
                val newSticker = StickerManager.addStickerFromUri(context, uri)
                val pack = StickerManager.getPackInfo(context)
                val auto = _uiState.value.autoTransfer

                _uiState.update {
                    it.copy(
                        packInfo = pack,
                        selectedSticker = newSticker,
                        isProcessing = false,
                        statusMessage = "Added ${newSticker.name} to pack!",
                        pendingTransferTarget = if (auto) TransferTarget.ALL else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Failed to process: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun onMultipleMediaPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Importing ${uris.size} stickers...") }
            var lastAdded: StickerItem? = null
            var successCount = 0
            for (uri in uris) {
                try {
                    lastAdded = StickerManager.addStickerFromUri(context, uri)
                    successCount++
                } catch (_: Exception) {
                }
            }
            val pack = StickerManager.getPackInfo(context)
            val auto = _uiState.value.autoTransfer

            _uiState.update {
                it.copy(
                    packInfo = pack,
                    selectedSticker = lastAdded ?: pack.stickers.lastOrNull(),
                    isProcessing = false,
                    statusMessage = "Successfully imported $successCount stickers!",
                    pendingTransferTarget = if (auto) TransferTarget.ALL else null
                )
            }
        }
    }

    fun selectSticker(sticker: StickerItem) {
        _uiState.update { it.copy(selectedSticker = sticker) }
    }

    fun removeSticker(stickerId: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            StickerManager.removeSticker(context, stickerId)
            val pack = StickerManager.getPackInfo(context)
            _uiState.update {
                it.copy(
                    packInfo = pack,
                    selectedSticker = pack.stickers.lastOrNull(),
                    statusMessage = "Sticker removed"
                )
            }
        }
    }

    fun clearPack() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            StickerManager.clearPack(context)
            _uiState.update {
                it.copy(
                    packInfo = StickerPackInfo(),
                    selectedSticker = null,
                    statusMessage = "Pack cleared"
                )
            }
        }
    }

    fun toggleAutoTransfer(enabled: Boolean) {
        _uiState.update { it.copy(autoTransfer = enabled) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearPendingTransfer() {
        _uiState.update { it.copy(pendingTransferTarget = null) }
    }

    fun showDialog(title: String, message: String, isSuccess: Boolean = true, targetApp: String? = null) {
        _uiState.update {
            it.copy(
                dialogInfo = TransferDialogInfo(
                    title = title,
                    message = message,
                    isSuccess = isSuccess,
                    targetApp = targetApp
                )
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogInfo = null) }
    }
}
