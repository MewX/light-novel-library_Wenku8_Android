package org.mewx.wenku8.async;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightNetwork;

import java.lang.ref.WeakReference;

public class CheckAppNewVersion extends AsyncTask<Void, Void, Integer> {
    private WeakReference<Context> contextWeakReference;
    private boolean verboseMode;

    public CheckAppNewVersion(Context context) {
        this(context, false);
    }

    /**
     * Check whether there's a new version of the app published.
     *
     * @param context the context used for showing dialogs.
     * @param verbose whether to actively show error messages.
     */
    public CheckAppNewVersion(Context context, boolean verbose) {
        this.contextWeakReference = new WeakReference<>(context);
        this.verboseMode = verbose;
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        // load latest version code
        byte[] codeByte = LightNetwork.LightHttpDownload(GlobalConfig.versionCheckUrl);
        if (codeByte == null) return -1; // time out

        // parse the version code
        String code = new String(codeByte).trim();
        Log.d("MewX", "latest version code: " + code);
        if (code.isEmpty() || !TextUtils.isDigitsOnly(code)) return -2; // parse error
        else return Integer.parseInt(code);
    }

    @Override
    protected void onPostExecute(Integer code) {
        super.onPostExecute(code);

        Context ctx = contextWeakReference.get();
        if (ctx == null) return;

        if (code < 0) {
            // Logging different errors.
            if (code == -1) {
                Log.e("MewX", "unable to fetch latest version");
            } else if (code == -2) {
                Log.e("MewX", "unable to parse version");
            }

            // TODO: show different error messages.
            if (verboseMode) {
                Toast.makeText(ctx, ctx.getResources().getString(R.string.system_update_timeout), Toast.LENGTH_SHORT).show();
            }
        }

        int current = BuildConfig.VERSION_CODE;
        if (current >= code) {
            Log.i("MewX", "no newer version");
            if (verboseMode) {
                Toast.makeText(ctx, ctx.getResources().getString(R.string.system_update_latest_version), Toast.LENGTH_SHORT).show();
            }
        } else {
            // update to new version
            new MaterialDialog.Builder(ctx)
                    .theme(Theme.LIGHT)
                    .title(R.string.system_update_found_new)
                    .content(R.string.system_update_jump_to_page)
                    .positiveText(R.string.dialog_positive_sure)
                    .negativeText(R.string.dialog_negative_biao)
                    .negativeColorRes(R.color.menu_text_color)
                    .onPositive((dialog, which) -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.blogPageUrl));
                        ctx.startActivity(browserIntent);
                    })
                    .show();
        }
    }
}
