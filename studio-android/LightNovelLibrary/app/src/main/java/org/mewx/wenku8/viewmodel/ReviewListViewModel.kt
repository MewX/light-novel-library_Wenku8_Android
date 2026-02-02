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
import org.mewx.wenku8.global.api.ReviewList
import org.mewx.wenku8.global.api.Wenku8Parser
import org.mewx.wenku8.network.LightNetwork
import java.nio.charset.Charset
import java.util.ArrayList

sealed interface ReviewListUiState {
    data object Loading : ReviewListUiState
    data class Success(
        val reviews: List<ReviewList.Review>,
        val currentPage: Int,
        val totalPages: Int,
        val isLoadingMore: Boolean = false
    ) : ReviewListUiState
    data object Error : ReviewListUiState
}

class ReviewListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ReviewListUiState>(ReviewListUiState.Loading)
    val uiState: StateFlow<ReviewListUiState> = _uiState.asStateFlow()

    private val reviewList = ReviewList()
    private var aid: Int = 0

    fun init(aid: Int) {
        if (this.aid == aid && _uiState.value !is ReviewListUiState.Loading) return
        this.aid = aid
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = ReviewListUiState.Loading
            reviewList.resetList()
            loadPage(1)
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState is ReviewListUiState.Success &&
            !currentState.isLoadingMore &&
            reviewList.currentPage < reviewList.totalPage) {

            _uiState.value = currentState.copy(isLoadingMore = true)
            viewModelScope.launch {
                loadPage(reviewList.currentPage + 1)
            }
        }
    }

    private suspend fun loadPage(page: Int) {
        withContext(Dispatchers.IO) {
            val params = Wenku8API.getCommentListParams(aid, page)
            val tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, params)

            if (tempXml == null) {
                if (_uiState.value is ReviewListUiState.Success) {
                     val currentState = _uiState.value as ReviewListUiState.Success
                     _uiState.value = currentState.copy(isLoadingMore = false)
                } else {
                    _uiState.value = ReviewListUiState.Error
                }
                return@withContext
            }

            val xml = String(tempXml, Charset.forName("UTF-8"))

            try {
                Wenku8Parser.parseReviewList(reviewList, xml)
                val newList = ArrayList(reviewList.list) // Copy
                _uiState.value = ReviewListUiState.Success(
                    reviews = newList,
                    currentPage = reviewList.currentPage,
                    totalPages = reviewList.totalPage
                )
            } catch (e: Exception) {
                e.printStackTrace()
                 if (_uiState.value !is ReviewListUiState.Success) {
                    _uiState.value = ReviewListUiState.Error
                } else {
                    val currentState = _uiState.value as ReviewListUiState.Success
                    _uiState.value = currentState.copy(isLoadingMore = false)
                }
            }
        }
    }
}
