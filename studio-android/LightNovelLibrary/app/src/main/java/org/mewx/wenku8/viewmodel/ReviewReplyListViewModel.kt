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
import org.mewx.wenku8.global.api.ReviewReplyList
import org.mewx.wenku8.global.api.Wenku8Parser
import org.mewx.wenku8.network.LightNetwork
import java.nio.charset.Charset
import java.util.ArrayList

sealed interface ReviewReplyListUiState {
    data object Loading : ReviewReplyListUiState
    data class Success(
        val replies: List<ReviewReplyList.ReviewReply>,
        val currentPage: Int,
        val totalPages: Int,
        val isLoadingMore: Boolean = false
    ) : ReviewReplyListUiState
    data object Error : ReviewReplyListUiState
}

sealed interface SendReplyUiState {
    data object Idle : SendReplyUiState
    data object Sending : SendReplyUiState
    data object Success : SendReplyUiState
    data class Error(val message: String) : SendReplyUiState
}

class ReviewReplyListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ReviewReplyListUiState>(ReviewReplyListUiState.Loading)
    val uiState: StateFlow<ReviewReplyListUiState> = _uiState.asStateFlow()

    private val _sendReplyState = MutableStateFlow<SendReplyUiState>(SendReplyUiState.Idle)
    val sendReplyState: StateFlow<SendReplyUiState> = _sendReplyState.asStateFlow()

    private var reviewReplyList = ReviewReplyList()
    private var rid: Int = 0

    fun init(rid: Int) {
        if (this.rid == rid && _uiState.value !is ReviewReplyListUiState.Loading) return
        this.rid = rid
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = ReviewReplyListUiState.Loading
            reviewReplyList = ReviewReplyList() // Reset
            loadPage(1)
        }
    }

    fun resetSendReplyState() {
        _sendReplyState.value = SendReplyUiState.Idle
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState is ReviewReplyListUiState.Success &&
            !currentState.isLoadingMore &&
            reviewReplyList.currentPage < reviewReplyList.totalPage) {

            _uiState.value = currentState.copy(isLoadingMore = true)
            viewModelScope.launch {
                loadPage(reviewReplyList.currentPage + 1)
            }
        }
    }

    private suspend fun loadPage(page: Int) {
        withContext(Dispatchers.IO) {
            val params = Wenku8API.getCommentContentParams(rid, page)
            val tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, params)

            if (tempXml == null) {
                if (_uiState.value is ReviewReplyListUiState.Success) {
                     val currentState = _uiState.value as ReviewReplyListUiState.Success
                     _uiState.value = currentState.copy(isLoadingMore = false)
                } else {
                    _uiState.value = ReviewReplyListUiState.Error
                }
                return@withContext
            }

            val xml = String(tempXml, Charset.forName("UTF-8"))
            try {
                Wenku8Parser.parseReviewReplyList(reviewReplyList, xml)
                val newList = ArrayList(reviewReplyList.list)
                _uiState.value = ReviewReplyListUiState.Success(
                    replies = newList,
                    currentPage = reviewReplyList.currentPage,
                    totalPages = reviewReplyList.totalPage
                )
            } catch (e: Exception) {
                e.printStackTrace()
                 if (_uiState.value !is ReviewReplyListUiState.Success) {
                    _uiState.value = ReviewReplyListUiState.Error
                } else {
                    val currentState = _uiState.value as ReviewReplyListUiState.Success
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            }
        }
    }

    fun sendReply(content: String) {
        if (_sendReplyState.value is SendReplyUiState.Sending) return

        viewModelScope.launch {
            _sendReplyState.value = SendReplyUiState.Sending
            withContext(Dispatchers.IO) {
                val tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getCommentReplyParams(rid, content))
                if (tempXml == null) {
                    _sendReplyState.value = SendReplyUiState.Error("Network Error")
                    return@withContext
                }

                try {
                    val xml = String(tempXml, Charset.forName("UTF-8")).trim()
                    val code = xml.toIntOrNull()

                    if (code == 1) {
                         _sendReplyState.value = SendReplyUiState.Success
                         refresh() // Refresh list after success
                    } else if (code == 11) {
                        _sendReplyState.value = SendReplyUiState.Error("Post Locked")
                    } else {
                        _sendReplyState.value = SendReplyUiState.Error("Error: $xml")
                    }
                } catch (e: Exception) {
                    _sendReplyState.value = SendReplyUiState.Error("Parse Error")
                }
            }
        }
    }
}
