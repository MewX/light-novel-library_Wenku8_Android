package org.mewx.wenku8.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.network.LightNetwork
import java.nio.charset.Charset

sealed interface SubmitPostUiState {
    data object Idle : SubmitPostUiState
    data object Submitting : SubmitPostUiState
    data object Success : SubmitPostUiState
    data class Error(val message: String) : SubmitPostUiState
}

class ReviewNewPostViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SubmitPostUiState>(SubmitPostUiState.Idle)
    val uiState: StateFlow<SubmitPostUiState> = _uiState.asStateFlow()

    fun submitPost(aid: Int, title: String, content: String) {
        if (_uiState.value is SubmitPostUiState.Submitting) return

        viewModelScope.launch {
            _uiState.value = SubmitPostUiState.Submitting
            withContext(Dispatchers.IO) {
                // TODO: adding "Sent from Android client" at the end of content.
                val tempXml = LightNetwork.LightHttpPostConnection(
                    Wenku8API.BASE_URL,
                    Wenku8API.getCommentNewThreadParams(aid, title, content)
                )

                if (tempXml == null) {
                    _uiState.value = SubmitPostUiState.Error("Network Error")
                    return@withContext
                }

                try {
                    val xml = String(tempXml, Charset.forName("UTF-8")).trim()
                    val code = xml.toIntOrNull()

                    if (code == 1) {
                        _uiState.value = SubmitPostUiState.Success
                    } else {
                        _uiState.value = SubmitPostUiState.Error("Error: $xml")
                    }
                } catch (e: Exception) {
                    _uiState.value = SubmitPostUiState.Error("Parse Error")
                }
            }
        }
    }
}
