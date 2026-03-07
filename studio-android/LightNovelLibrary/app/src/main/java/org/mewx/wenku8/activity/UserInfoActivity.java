package org.mewx.wenku8.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;
import org.mewx.wenku8.util.GoogleServicesHelper;
import com.makeramen.roundedimageview.RoundedImageView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.UserInfo;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.api.Wenku8Error;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.network.LightUserSession;
import org.mewx.wenku8.util.ProgressDialogHelper;

import java.io.UnsupportedEncodingException;

/**
 * Created by MewX on 2015/6/14.
 * User Info Activity.
 */
public class UserInfoActivity extends BaseMaterialActivity {

    // private vars
    private FirebaseAnalytics mFirebaseAnalytics;
    private RoundedImageView rivAvatar;
    private TextView tvUserName, tvNickyName, tvScore, tvExperience, tvRank;
    private TextView tvLogout;
    private UserInfo ui;
    private AsyncGetUserInfo agui;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_account_info);

        // Init Firebase Analytics on GA4.
        mFirebaseAnalytics = GoogleServicesHelper.initFirebase(this);

        // get views
        rivAvatar = findViewById(R.id.user_avatar);
        tvUserName = findViewById(R.id.username);
        tvNickyName = findViewById(R.id.nickname);
        tvScore = findViewById(R.id.score);
        tvExperience = findViewById(R.id.experience);
        tvRank = findViewById(R.id.rank);
        tvLogout = findViewById(R.id.btn_logout);

        loadCachedAvatarInitially();

        // sync get info
        agui = new AsyncGetUserInfo();
        agui.execute();

    }

    private void loadCachedAvatarInitially() {
        // Spin up a quick background thread so we don't freeze the main UI
        new Thread(() -> {
            String avatarPath = null;
            
            // Check which cache file exists
            if (LightCache.testFileExist(GlobalConfig.getFirstUserAvatarSaveFilePath())) {
                avatarPath = GlobalConfig.getFirstUserAvatarSaveFilePath();
            } else if (LightCache.testFileExist(GlobalConfig.getSecondUserAvatarSaveFilePath())) {
                avatarPath = GlobalConfig.getSecondUserAvatarSaveFilePath();
            }

            // If we found a cached file, decode it
            if (avatarPath != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2; // Keep the same memory-saving compression
                Bitmap cachedBitmap = BitmapFactory.decodeFile(avatarPath, options);

                // If decoding succeeded, push it to the UI thread to display
                if (cachedBitmap != null) {
                    runOnUiThread(() -> {
                        if (rivAvatar != null) {
                            rivAvatar.setImageBitmap(cachedBitmap);
                        }
                    });
                }
            }
        }).start();
    }

    // The result type is an Object array so we can pass back both the ErrorCode AND the Bitmap/UserInfo cleanly
    private class AsyncGetUserInfo extends AsyncTask<Integer, Void, Object[]> {
        private boolean isSignOperation;
        private ProgressDialogHelper md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = ProgressDialogHelper.show(UserInfoActivity.this,
                    R.string.system_fetching,
                    /* indeterminate= */ true, /* cancelable= */ false, /* cancelListener= */ null);
        }

        @Override
        protected Object[] doInBackground(Integer... params) {
            isSignOperation = (params.length == 1 && params[0] == 1);  // 0 is fetch data, 1 is sign
            Wenku8Error.ErrorCode signStatus = null;

            try {
                // 1. Handle Signing (if requested)
                if (isSignOperation) {
                    byte[] signBytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getUserSignParams());
                    if (signBytes == null) return new Object[]{Wenku8Error.ErrorCode.NETWORK_ERROR, null, null};
                    
                    String signResponse = new String(signBytes, "UTF-8");
                    if (!LightTool.isInteger(signResponse)) {
                        return new Object[]{Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR, null, null};
                    }
                    
                    signStatus = Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(signResponse));
                    if (signStatus == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED) {
                        return new Object[]{signStatus, null, null}; // Return early if sign fails
                    }
                }

                // 2. Fetch User Info
                byte[] infoBytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getUserInfoParams());
                if (infoBytes == null) return new Object[]{Wenku8Error.ErrorCode.NETWORK_ERROR, null, null};

                String xml = new String(infoBytes, "UTF-8");
                
                // Handle auto-login if needed
                if (LightTool.isInteger(xml) && Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(xml)) == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                    Wenku8Error.ErrorCode loginTemp = LightUserSession.doLoginFromFile(GlobalConfig::loadUserInfoSet);
                    if (loginTemp != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) return new Object[]{loginTemp, null, null};
                    
                    infoBytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getUserInfoParams());
                    if (infoBytes == null) return new Object[]{Wenku8Error.ErrorCode.NETWORK_ERROR, null, null};
                    xml = new String(infoBytes, "UTF-8");
                } else if (LightTool.isInteger(xml)) {
                    return new Object[]{Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(xml)), null, null};
                }

                UserInfo parsedUi = UserInfo.parseUserInfo(xml);
                if (parsedUi == null) return new Object[]{Wenku8Error.ErrorCode.XML_PARSE_FAILED, null, null};

                // 3. Force Fetch Avatar & Decode Directly from Memory
                byte[] avatarBytes = LightNetwork.LightHttpDownload(Wenku8API.getAvatarURL(parsedUi.uid));
                Bitmap decodedAvatar = null;
                
                if (avatarBytes != null && avatarBytes.length > 0) {
                    // Decode straight from the byte array. This guarantees we show the fresh download.
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2; 
                    decodedAvatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length, options);

                    // Save to disk cache for offline/future use
                    String avatarPath = GlobalConfig.getFirstUserAvatarSaveFilePath();
                    if (!LightCache.saveFile(avatarPath, avatarBytes, true)) {
                        LightCache.saveFile(GlobalConfig.getSecondUserAvatarSaveFilePath(), avatarBytes, true);
                    }
                }

                // Pass everything back to the UI thread cleanly
                return new Object[]{Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED, parsedUi, decodedAvatar};

            } catch (Exception e) {
                e.printStackTrace();
                return new Object[]{Wenku8Error.ErrorCode.NETWORK_ERROR, null, null};
            }
        }

        @Override
        protected void onPostExecute(Object[] result) {
            super.onPostExecute(result);
            if (md != null) md.dismiss();

            Wenku8Error.ErrorCode errorCode = (Wenku8Error.ErrorCode) result[0];
            UserInfo fetchedUi = (UserInfo) result[1];
            Bitmap fetchedAvatar = (Bitmap) result[2];

            // Analytics and Toasts for Sign Operation
            if (isSignOperation) {
                Bundle checkInParams = new Bundle();
                checkInParams.putString("effective_click", "" + (errorCode != Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED));
                GoogleServicesHelper.logEvent(mFirebaseAnalytics, "daily_check_in", checkInParams);

                int toastMsg = (errorCode == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED) ? R.string.userinfo_sign_failed : R.string.userinfo_sign_successful;
                Toast.makeText(UserInfoActivity.this, getResources().getString(toastMsg), Toast.LENGTH_SHORT).show();
                
                if (errorCode == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED) return;
            }

            // Apply UI Updates
            if (errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED && fetchedUi != null) {
                ui = fetchedUi; 
                
                // If the memory decode succeeded, apply it instantly
                if (fetchedAvatar != null) {
                    rivAvatar.setImageBitmap(fetchedAvatar);
                }

                tvUserName.setText(ui.username);
                tvNickyName.setText(ui.nickyname);
                tvScore.setText(Integer.toString(ui.score));
                tvExperience.setText(Integer.toString(ui.experience));
                tvRank.setText(ui.rank);
                
                tvLogout.setOnClickListener(v -> new MaterialAlertDialogBuilder(UserInfoActivity.this)
                        .setMessage(R.string.dialog_content_sure_to_logout)
                        .setPositiveButton(R.string.dialog_positive_ok, (dialog, which) -> new AsyncLogout().execute())
                        .setNegativeButton(R.string.dialog_negative_biao, null)
                        .show());
            } else if (errorCode != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(UserInfoActivity.this, errorCode.toString(), Toast.LENGTH_SHORT).show();
                UserInfoActivity.this.finish();
            }
        }
    }

    private class AsyncLogout extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private ProgressDialogHelper md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = ProgressDialogHelper.show(UserInfoActivity.this,
                    R.string.system_fetching,
                    /* indeterminate= */ true, /* cancelable= */ false, /* cancelListener= */ null);
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getUserLogoutParams());
            if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            try {
                String result = new String(b, "UTF-8");
                if(!LightTool.isInteger(result)) {
                    return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
                }

                return Wenku8Error.getSystemDefinedErrorCode(new Integer(result)); // get 1 or 4 exceptions
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED || errorCode == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                LightUserSession.logOut(() -> {
                    // TODO: extract this to a util.
                    // delete files
                    LightCache.deleteFile(GlobalConfig.getFirstFullUserAccountSaveFilePath());
                    LightCache.deleteFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
                    LightCache.deleteFile(GlobalConfig.getFirstUserAvatarSaveFilePath());
                    LightCache.deleteFile(GlobalConfig.getSecondUserAvatarSaveFilePath());
                });
                Toast.makeText(UserInfoActivity.this, "Logged out!", Toast.LENGTH_SHORT).show();
            }
            else
                Toast.makeText(UserInfoActivity.this, errorCode.toString(), Toast.LENGTH_SHORT).show();

            // terminate this activity
            if (md != null) {
                md.dismiss();
            }
            UserInfoActivity.this.finish();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_user_info, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        else if(menuItem.getItemId() == R.id.action_sign) {
            if(agui != null && agui.getStatus() == AsyncTask.Status.FINISHED) {
                // do sign operation
                agui = new AsyncGetUserInfo();
                agui.execute(1);

            }
            else
                Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
