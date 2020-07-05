package org.mewx.wenku8.activity;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightNetwork;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by MewX on 2020/7/5.
 * Novel Review New Post Activity.
 */
public class NovelReviewNewPostActivity extends AppCompatActivity {
    // private vars
    private static int aid = 1;

    // components
    private EditText etTitle;
    private EditText etContent;

    // switcher
    private static AtomicBoolean isSubmitting = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_review_new_post);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);

        // set indicator enable
        Toolbar mToolbar = findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
        if (getSupportActionBar() != null && upArrow != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16) {
            // create our manager instance after the content view is set
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintAlpha(0.15f);
            tintManager.setNavigationBarAlpha(0.0f);
            // set all color
            tintManager.setTintColor(getResources().getColor(android.R.color.black));
            // set Navigation bar color
            if (Build.VERSION.SDK_INT >= 21)
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColor));
        }

        // get views
        etTitle = findViewById(R.id.input_title);
        etContent = findViewById(R.id.input_content);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_review_new_post, menu);
        return true;
    }

    private boolean noBadWords(String text) {
        String badWord = Wenku8API.searchBadWords(text);
        if (badWord != null) {
            Toast.makeText(getApplication(), String.format(getResources().getString(R.string.system_containing_bad_word), badWord), Toast.LENGTH_SHORT).show();
            return false;
        } else if (text.length() < Wenku8API.MIN_REPLY_TEXT) {
            Toast.makeText(getApplication(), getResources().getString(R.string.system_review_too_short), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void hideIME() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (menuItem.getItemId() == R.id.action_submit) {
            String title = etTitle.getText().toString();
            String content = etContent.getText().toString();
            if (noBadWords(title) && noBadWords(content)) {
                hideIME();
                new AsyncSubmitNePost(this, title, content).execute();
            }
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        // TODO: save draft

        if (etTitle.getText().toString().trim().length() != 0 ||
                etContent.getText().toString().trim().length() != 0) {
            new MaterialDialog.Builder(this)
                    .theme(Theme.LIGHT)
                    .title(R.string.system_warning)
                    .content(R.string.system_review_draft_will_be_lost)
                    .positiveText(R.string.dialog_positive_ok)
                    .negativeText(R.string.dialog_negative_preferno)
                    .negativeColorRes(R.color.menu_text_color)
                    .onPositive((dialog, which) -> {
                        super.onBackPressed();
                    })
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    private static class AsyncSubmitNePost extends AsyncTask<Void, Void, Integer> {
        private String title, content;
        private boolean ran = false; // whether the action is done by this instance.

        private WeakReference<NovelReviewNewPostActivity> activityWeakReference;

        AsyncSubmitNePost(NovelReviewNewPostActivity activity, String title, String content) {
            this.activityWeakReference = new WeakReference<>(activity);
            this.title = title;
            this.content = content;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (!isSubmitting.getAndSet(true)) {
                ran = true;
                Log.d(NovelReviewNewPostActivity.class.getSimpleName(),
                        String.format("start submitting: [%s] %s", title, content));

                // todo: disable icon or change it to an animated icon
            }
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            if (!ran) return null;

            // TODO: adding "Sent from Android client" at the end of content.

            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                    Wenku8API.getCommentNewThreadParams(aid, title, content));
            if (tempXml == null) return 0; // network issue
            String xml = new String(tempXml, Charset.forName("UTF-8")).trim();
            Log.d(NovelReviewNewPostActivity.class.getSimpleName(), xml);
            return Integer.valueOf(xml);
        }

        @Override
        protected void onPostExecute(Integer errorCode) {
            super.onPostExecute(errorCode);
            if (!ran) return;

            NovelReviewNewPostActivity activity = activityWeakReference.get();
            if (errorCode == null || errorCode != 1) {
                // net network or other issue
                if (activity != null) {
                    Toast.makeText(activity, activity.getResources().getString(R.string.system_network_error), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // errorCode is 1 - successful.
            isSubmitting.set(false);
            Log.d(NovelReviewNewPostActivity.class.getSimpleName(), "finished submitting");

            // todo: clean saved draft

            // todo: return to previous screen and refresh
            if (activity != null) {
                activity.finish();
            }
        }
    }

}
