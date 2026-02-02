package org.mewx.wenku8.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.analytics.FirebaseAnalytics
import org.mewx.wenku8.theme.Wenku8Theme
import org.mewx.wenku8.ui.ReviewReplyListScreen

class NovelReviewReplyListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Firebase Analytics
        FirebaseAnalytics.getInstance(this)

        val rid = intent.getIntExtra("rid", 1)
        val title = intent.getStringExtra("title")

        setContent {
            Wenku8Theme {
                ReviewReplyListScreen(
                    rid = rid,
                    title = title,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
