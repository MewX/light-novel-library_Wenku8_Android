package org.mewx.wenku8.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.umeng.analytics.MobclickAgent;
import com.umeng.onlineconfig.OnlineConfigAgent;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.AboutActivity;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.MenuBackgroundSelectorActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // get views
        Wenku8API.NoticeString = OnlineConfigAgent.getInstance().getConfigParams(MyApp.getContext(),
                GlobalConfig.getCurrentLang() != Wenku8API.LANG.SC ? "wenku8_notice_tw" : "wenku8_notice"); // get each time
        TextView tvNotice = (TextView) getActivity().findViewById(R.id.notice);
        if(Wenku8API.NoticeString.equals(""))
            getActivity().findViewById(R.id.notice_layout).setVisibility(View.GONE);
        else
            tvNotice.setText("通知：\n" + Wenku8API.NoticeString);

        getActivity().findViewById(R.id.btn_choose_language).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(getActivity())
                        .theme(Theme.LIGHT)
                        .title(R.string.config_choose_language)
                        .content(R.string.dialog_content_language_tip)
                        .items(R.array.choose_language_option)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        // sc
                                        if(GlobalConfig.getCurrentLang() != Wenku8API.LANG.SC) {
                                            GlobalConfig.setCurrentLang(Wenku8API.LANG.SC);
                                            Intent intent = new Intent();
                                            intent.setClass(getActivity(), MainActivity.class);
                                            startActivity(intent);
                                            getActivity().overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                            getActivity().finish(); // destroy itself
                                        }
                                        else
                                            Toast.makeText(getActivity(), "Already in.", Toast.LENGTH_SHORT).show();
                                        break;
                                    case 1:
                                        // tc
                                        if(GlobalConfig.getCurrentLang() != Wenku8API.LANG.TC) {
                                            GlobalConfig.setCurrentLang(Wenku8API.LANG.TC);
                                            Intent intent = new Intent();
                                            intent.setClass(getActivity(), MainActivity.class);
                                            startActivity(intent);
                                            getActivity().overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                            getActivity().finish(); // destroy itself
                                        }
                                        else
                                            Toast.makeText(getActivity(), "Already in.", Toast.LENGTH_SHORT).show();
                                        break;
                                }
                            }
                        })
                        .show();
            }
        });
        getActivity().findViewById(R.id.btn_clear_cache).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(getActivity())
                        .theme(Theme.LIGHT)
                        .title(R.string.config_clear_cache)
                        .items(R.array.wipe_cache_option)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        // fast mode
                                        AsyncDeleteFast adf = new AsyncDeleteFast();
                                        adf.execute();
                                        break;
                                    case 1:
                                        // slow mode
                                        AsyncDeleteSlow ads = new AsyncDeleteSlow();
                                        ads.execute();
                                        break;
                                }
                            }
                        })
                        .show();
            }
        });
        getActivity().findViewById(R.id.btn_navigation_drawer_wallpaper).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), MenuBackgroundSelectorActivity.class);
                startActivity(intent);
            }
        });
        getActivity().findViewById(R.id.btn_check_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // alpha version does not contains auto-update function
                // check for update
                new AsyncTask<String, Integer, Integer>() {
                    @Override
                    protected Integer doInBackground(String... strings) {
                        // return version code
                        byte[] codeByte = LightNetwork.LightHttpDownload(strings[0]);
                        if (codeByte == null) return -1;
                        String code = new String(codeByte);
                        Log.d("MewX", "version code: " + code);
                        if (code.trim().isEmpty() || !TextUtils.isDigitsOnly(code.trim())) return -1;
                        else return Integer.parseInt(code);
                    }

                    @Override
                    protected void onPostExecute(Integer code) {
                        super.onPostExecute(code);
                        if (code == -1)
                            Toast.makeText(getActivity(), getResources().getString(R.string.system_update_timeout), Toast.LENGTH_SHORT).show();
                        try {
                            int current = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionCode;
                            Log.d("MewX", "current version code: " + current);
                            if (current >= code) {
                                Toast.makeText(getActivity(), getResources().getString(R.string.system_update_latest_version), Toast.LENGTH_SHORT).show();
                            }
                            else {
                                // update to new version
                                new MaterialDialog.Builder(getContext())
                                        .theme(Theme.LIGHT)
                                        .title(R.string.system_update_found_new)
                                        .content(R.string.system_update_jump_to_page)
                                        .positiveText(R.string.dialog_positive_sure)
                                        .negativeText(R.string.dialog_negative_biao)
                                        .callback(new MaterialDialog.ButtonCallback() {
                                            @Override
                                            public void onPositive(MaterialDialog dialog) {
                                                super.onPositive(dialog);
                                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://wenku8.mewx.org/"));
                                                startActivity(browserIntent);
                                            }
                                        })
                                        .show();

                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }.execute("http://wenku8.mewx.org/version");
            }
        });
        getActivity().findViewById(R.id.btn_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), AboutActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
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

    private class AsyncDeleteFast extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(getActivity())
                    .theme(Theme.LIGHT)
                    .title(R.string.config_clear_cache)
                    .content(R.string.dialog_content_wipe_cache_fast)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // covers
            File dir = new File(GlobalConfig.getFirstStoragePath() + "imgs");
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondStoragePath() + "imgs");
            File[] childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    String[] temp = f.getAbsolutePath().split("\\/");
                    if(temp.length != 0) {
                        String id = temp[temp.length - 1].split("\\.")[0];
                        if(LightTool.isInteger(id) && !GlobalConfig.testInLocalBookshelf(Integer.parseInt(id)))
                            f.delete(); // ignore ".nomedia"
                    }
                }
            }

            // cache
            dir = new File(GlobalConfig.getFirstStoragePath() + "cache");
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondStoragePath() + "cache");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    f.delete();
                }
            }
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            if(md != null) md.dismiss();
            Toast.makeText(getActivity(), "OK", Toast.LENGTH_SHORT).show();
        }
    }

    private class AsyncDeleteSlow extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md;
        private boolean isLoading = false;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(getActivity())
                    .theme(Theme.LIGHT)
                    .cancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            isLoading = false;
                            AsyncDeleteSlow.this.cancel(true);
                        }
                    })
                    .title(R.string.config_clear_cache)
                    .content(R.string.dialog_content_wipe_cache_slow)
                    .progress(true, 0)
                    .cancelable(true)
                    .show();
            isLoading = true;
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // covers
            File dir = new File(GlobalConfig.getFirstStoragePath() + "imgs");
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondStoragePath() + "imgs");
            File[] childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    String[] temp = f.getAbsolutePath().split("\\/");
                    if(temp.length != 0) {
                        String id = temp[temp.length - 1].split("\\.")[0];
                        if(LightTool.isInteger(id) && !GlobalConfig.testInLocalBookshelf(Integer.parseInt(id)))
                            f.delete(); // ignore ".nomedia"
                    }
                }
            }

            // cache
            dir = new File(GlobalConfig.getFirstStoragePath() + "cache");
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondStoragePath() + "cache");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) f.delete();
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
            if(!dir.exists()) dir = new File(GlobalConfig.getSecondFullSaveFilePath() + "imgs");
            childFile = dir.listFiles();
            if(childFile != null && childFile.length != 0) {
                for (File f : childFile) {
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    String[] temp = f.getAbsolutePath().split("\\/");
                    if(temp.length != 0) {
                        String name = temp[temp.length - 1];
                        if(!listPicture.contains(name)) f.delete();
                    }
                }
            }
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            isLoading = false;
            if(md != null) md.dismiss();

            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(getActivity(), "OK", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getActivity(), errorCode.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
