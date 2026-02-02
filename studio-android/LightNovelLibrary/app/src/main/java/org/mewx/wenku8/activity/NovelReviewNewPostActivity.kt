package org.mewx.wenku8.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.analytics.FirebaseAnalytics
import org.mewx.wenku8.theme.Wenku8Theme
import org.mewx.wenku8.ui.ReviewNewPostScreen

class NovelReviewNewPostActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Firebase Analytics
        FirebaseAnalytics.getInstance(this)

        val aid = intent.getIntExtra("aid", 1)

        setContent {
            Wenku8Theme {
                ReviewNewPostScreen(
                    aid = aid,
                    onBackClick = { finish() },
                    onSuccess = { finish() }
                )
            }
        }
    }
}
