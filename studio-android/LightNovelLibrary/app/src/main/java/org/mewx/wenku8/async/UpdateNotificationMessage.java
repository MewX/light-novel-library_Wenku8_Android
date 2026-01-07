package org.mewx.wenku8.async;

import android.os.AsyncTask;
import android.util.Log;

import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.network.LightNetwork;

/**
 * This async task is used for checking new notification texts.
 */
public class UpdateNotificationMessage extends AsyncTask<Void, Void, String> {
    @Override
    protected String doInBackground(Void... voids) {
        byte[] codeByte = LightNetwork.LightHttpDownload(
                GlobalConfig.getCurrentLang() != Wenku8API.AppLanguage.SC ?
                        GlobalConfig.noticeCheckTc : GlobalConfig.noticeCheckSc
        );

        if (codeByte == null) {
            Log.e(UpdateNotificationMessage.class.getSimpleName(), "unable to get notification text");
            return null;
        }

        String notice = new String(codeByte);
        return notice.trim();
    }

    @Override
    protected void onPostExecute(String notice) {
        super.onPostExecute(notice);

        if (notice == null || notice.isEmpty()) {
            Log.e(UpdateNotificationMessage.class.getSimpleName(), "received empty notification text");
            return;
        }

        Log.i("MewX", "received notification text: " + notice);

        // update the latest string
        Wenku8API.NoticeString = notice;
        // save to local file
        GlobalConfig.writeTheNotice(notice);
    }
}
