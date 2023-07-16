package org.mewx.wenku8.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.AboutActivity;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.MenuBackgroundSelectorActivity;
import org.mewx.wenku8.async.CheckAppNewVersion;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightTool;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigFragment extends Fragment {

    public static ConfigFragment newInstance() {
        return new ConfigFragment();
    }

    public ConfigFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // get views
        TextView tvNotice = Objects.requireNonNull(getActivity()).findViewById(R.id.notice);
        if(Wenku8API.NoticeString.equals(""))
            getActivity().findViewById(R.id.notice_layout).setVisibility(View.GONE);
        else {
            CharSequence sequence = Html.fromHtml(Wenku8API.NoticeString.trim());
            // remove trailing spaces
            int i = sequence.length() - 1;
            while (i >= 0 && Character.isWhitespace(sequence.charAt(i))) i--;
            sequence = sequence.subSequence(0, i + 1);

            // parse html
            SpannableStringBuilder strBuilder = new SpannableStringBuilder(sequence);
            URLSpan[] urls = strBuilder.getSpans(0, sequence.length(), URLSpan.class);
            for(URLSpan span : urls) {
                int start = strBuilder.getSpanStart(span);
                int end = strBuilder.getSpanEnd(span);
                int flags = strBuilder.getSpanFlags(span);
                ClickableSpan clickable = new ClickableSpan() {
                    public void onClick(View view) {
                        // Do something with span.getURL() to handle the link click...
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.blogPageUrl));
                        Objects.requireNonNull(getContext()).startActivity(browserIntent);
                    }
                };
                strBuilder.setSpan(clickable, start, end, flags);
                strBuilder.removeSpan(span);
            }
            tvNotice.setText(strBuilder);
            tvNotice.setMovementMethod(LinkMovementMethod.getInstance());
        }

        getActivity().findViewById(R.id.btn_choose_language).setOnClickListener(v -> new MaterialDialog.Builder(getActivity())
                .theme(Theme.LIGHT)
                .title(R.string.config_choose_language)
                .content(R.string.dialog_content_language_tip)
                .items(R.array.choose_language_option)
                .itemsCallback((dialog, view, which, text) -> {
                    // 0 means Simplified Chinese; 1 means Traditional Chinese.
                    Wenku8API.LANG selected = which == 0 ? Wenku8API.LANG.SC : Wenku8API.LANG.TC;
                    if (selected == GlobalConfig.getCurrentLang()) {
                        Toast.makeText(getActivity(), "Already in.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Selected a different languages.
                    GlobalConfig.setCurrentLang(selected);
                    Intent intent = new Intent();
                    intent.setClass(getActivity(), MainActivity.class);
                    startActivity(intent);
                    getActivity().overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                    getActivity().finish(); // destroy itself
                })
                .show());
        getActivity().findViewById(R.id.btn_clear_cache).setOnClickListener(v -> new MaterialDialog.Builder(getActivity())
                .theme(Theme.LIGHT)
                .title(R.string.config_clear_cache)
                .items(R.array.wipe_cache_option)
                .itemsCallback((dialog, view, which, text) -> {
                    if (which == 0) {
                        new AsyncDeleteFast(getActivity()).execute();
                    } else if (which == 1) {
                        new AsyncDeleteSlow(getActivity()).execute();
                    }
                })
                .show());
        getActivity().findViewById(R.id.btn_navigation_drawer_wallpaper).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MenuBackgroundSelectorActivity.class);
            startActivity(intent);
        });
        getActivity().findViewById(R.id.btn_check_update).setOnClickListener(v -> {
            new CheckAppNewVersion(getActivity(), true).execute();
        });
        getActivity().findViewById(R.id.btn_about).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AboutActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("ConfigFragment");
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("ConfigFragment");
    }

    private static class AsyncDeleteFast extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private WeakReference<Context> contextWeakReference;
        private MaterialDialog md;

        AsyncDeleteFast(Context context) {
            this.contextWeakReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Context ctx = contextWeakReference.get();
            if (ctx != null) {
                md = new MaterialDialog.Builder(ctx)
                        .theme(Theme.LIGHT)
                        .title(R.string.config_clear_cache)
                        .content(R.string.dialog_content_wipe_cache_fast)
                        .progress(true, 0)
                        .cancelable(false)
                        .show();
            }
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // covers
            File dir = new File(GlobalConfig.getDefaultStoragePath() + "imgs");
            if(!dir.exists()) dir = new File(GlobalConfig.getBackupStoragePath() + "imgs");
            File[] childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    String[] temp = f.getAbsolutePath().split("/");
                    if(temp.length != 0) {
                        String id = temp[temp.length - 1].split("\\.")[0];
                        if(LightTool.isInteger(id) && !GlobalConfig.testInLocalBookshelf(Integer.parseInt(id)) && !f.delete())
                            Log.d(ConfigFragment.class.getSimpleName(), "Failed to delete file: " + f.getAbsolutePath()); // ignore ".nomedia"
                    }
                }
            }

            // cache
            dir = new File(GlobalConfig.getDefaultStoragePath() + "cache");
            if(!dir.exists()) dir = new File(GlobalConfig.getBackupStoragePath() + "cache");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    if (!f.delete())
                        Log.d(ConfigFragment.class.getSimpleName(), "Failed to delete file: " + f.getAbsolutePath());
                }
            }
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            if(md != null) md.dismiss();

            Context ctx = contextWeakReference.get();
            if (ctx != null) {
                Toast.makeText(ctx, "OK", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class AsyncDeleteSlow extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private WeakReference<Context> contextWeakReference;
        private MaterialDialog md;
        private boolean isLoading = false;

        AsyncDeleteSlow(Context context) {
            this.contextWeakReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Context ctx = contextWeakReference.get();
            if (ctx != null) {
                md = new MaterialDialog.Builder(Objects.requireNonNull(ctx))
                        .theme(Theme.LIGHT)
                        .cancelListener(dialog -> {
                            isLoading = false;
                            AsyncDeleteSlow.this.cancel(true);
                        })
                        .title(R.string.config_clear_cache)
                        .content(R.string.dialog_content_wipe_cache_slow)
                        .progress(true, 0)
                        .cancelable(true)
                        .show();
            }
            isLoading = true;
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // covers
            File dir = new File(GlobalConfig.getDefaultStoragePath() + "imgs");
            if(!dir.exists()) dir = new File(GlobalConfig.getBackupStoragePath() + "imgs");
            File[] childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    String[] temp = f.getAbsolutePath().split("/");
                    if(temp.length != 0) {
                        String id = temp[temp.length - 1].split("\\.")[0];
                        if(LightTool.isInteger(id) && !GlobalConfig.testInLocalBookshelf(Integer.parseInt(id)) && !f.delete())
                            Log.d(ConfigFragment.class.getSimpleName(), "Failed to delete file: " + f.getAbsolutePath()); // ignore ".nomedia"
                    }
                }
            }

            // cache
            dir = new File(GlobalConfig.getDefaultStoragePath() + "cache");
            if(!dir.exists()) dir = new File(GlobalConfig.getBackupStoragePath() + "cache");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    if (!f.delete())
                        Log.d(ConfigFragment.class.getSimpleName(), "Failed to delete file: " + f.getAbsolutePath());
                }
            }
            if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;

            // get saved picture filename list. Rec all /wenku8/saves/novel, get all picture name, then delete if not in list
            List<String> listPicture = new ArrayList<>();
            dir = new File(GlobalConfig.getFirstFullSaveFilePath() + "novel");
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondFullSaveFilePath() + "novel");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    byte[] temp = LightCache.loadFile(f.getAbsolutePath());
                    if(temp == null) continue;
                    try {
                        List<OldNovelContentParser.NovelContent> list = OldNovelContentParser.NovelContentParser_onlyImage(new String(temp, "UTF-8"));
                        for(OldNovelContentParser.NovelContent nv : list) listPicture.add(GlobalConfig.generateImageFileNameByURL(nv.content));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            // loop for images
            dir = new File(GlobalConfig.getFirstFullSaveFilePath() + "imgs");
            if (!dir.exists()) dir = new File(GlobalConfig.getSecondFullSaveFilePath() + "imgs");
            childFile = dir.listFiles();
            if (childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    if (!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    String[] temp = f.getAbsolutePath().split("/");
                    if (temp.length != 0) {
                        String name = temp[temp.length - 1];
                        if (!listPicture.contains(name) && !f.delete())
                            Log.d(ConfigFragment.class.getSimpleName(), "Failed to delete file: " + f.getAbsolutePath());
                    }
                }
            }
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            isLoading = false;
            if (md != null) md.dismiss();

            Context ctx = contextWeakReference.get();
            if (ctx == null) return;

            if (errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(ctx, "OK", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, errorCode.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
