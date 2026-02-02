package org.mewx.wenku8.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.analytics.FirebaseAnalytics
import org.mewx.wenku8.R
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.theme.Wenku8Theme
import org.mewx.wenku8.ui.ReviewListScreen

class NovelReviewListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Firebase Analytics
        FirebaseAnalytics.getInstance(this)

        val aid = intent.getIntExtra("aid", 1)

        setContent {
            Wenku8Theme {
                ReviewListScreen(
                    aid = aid,
                    onBackClick = { finish() },
                    onItemClick = { review ->
                        val intent = Intent(this, NovelReviewReplyListActivity::class.java)
                        intent.putExtra("rid", review.rid)
                        intent.putExtra("title", review.title)
                        startActivity(intent)
                    },
                    onNewPostClick = {
                        if (!LightUserSession.getLogStatus()) {
                            Toast.makeText(this, R.string.system_not_logged_in, Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(this, NovelReviewNewPostActivity::class.java)
                            intent.putExtra("aid", aid)
                            startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}
