package org.mewx.wenku8.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.makeramen.roundedimageview.RoundedImageView;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.UserInfo;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.LightUserSession;

import java.io.UnsupportedEncodingException;

/**
 * Created by MewX on 2015/6/14.
 * User Info Activity.
 */
public class UserInfoActivity extends AppCompatActivity {

    // private vars
    private RoundedImageView rivAvatar;
    private TextView tvUserName, tvNickyName, tvScore, tvExperience, tvRank;
    private TextView tvLogout;
    private UserInfo ui;
    private AsyncGetUserInfo agui;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_account_info);

        // set indicator enable
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_material);
            if(upArrow != null)
                upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) { //&& Build.VERSION.SDK_INT <= 21) {
            // Android API 22 has more effects on status bar, so ignore

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
            if(Build.VERSION.SDK_INT >= 21)
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColor));
        }

        // get views
        rivAvatar = (RoundedImageView)findViewById(R.id.user_avatar);
        tvUserName = (TextView)findViewById(R.id.username);
        tvNickyName = (TextView)findViewById(R.id.nickname);
        tvScore = (TextView)findViewById(R.id.score);
        tvExperience = (TextView)findViewById(R.id.experience);
        tvRank = (TextView)findViewById(R.id.rank);
        tvLogout = (TextView)findViewById(R.id.btn_logout);

        // sync get info
        agui = new AsyncGetUserInfo();
        agui.execute();

    }

    private class AsyncGetUserInfo extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private int operation; // 0 is fetch data, 1 is sign
        MaterialDialog md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            operation = 0; // init
            md = new MaterialDialog.Builder(UserInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .content(R.string.system_fetching)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            if(params.length == 1 && params[0] == 1) {
                // do sign, then fetch all data
                operation = 1;
                byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserSignParams());
                if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                try {
                    if(!LightTool.isInteger(new String(b))) return Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR;
                    else if(Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(new String(b))) == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED)
                        return Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED;
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            try {
                // try fetch
                byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserInfoParams());
                if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

                String xml = new String(b, "UTF-8");
                if(LightTool.isInteger(xml)) {
                    if(Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(xml)) == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                        // do log in
                        Wenku8Error.ErrorCode temp = LightUserSession.doLoginFromFile();
                        if(temp != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) return temp; // return an error code

                        // rquest again
                        b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserInfoParams());
                        if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                        xml = new String(b, "UTF-8");
                    }
                    else return Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(xml));
                }

                Log.e("MewX", xml);
                ui = UserInfo.parseUserInfo(new UserInfo(), xml);
                if(ui == null) return Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
            } catch (Exception e) {
                e.printStackTrace();
                return Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            md.dismiss();
            if(operation == 1) {
                // fetch from sign
                if(errorCode == Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED)
                    Toast.makeText(UserInfoActivity.this, getResources().getString(R.string.userinfo_sign_failed), Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(UserInfoActivity.this, getResources().getString(R.string.userinfo_sign_successful), Toast.LENGTH_SHORT).show();
                return; // just return
            }

            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                // show avatar
                String avatarPath;
                if(LightCache.testFileExist(GlobalConfig.getFirstUserAvatarSaveFilePath()))
                    avatarPath = GlobalConfig.getFirstUserAvatarSaveFilePath();
                else
                    avatarPath = GlobalConfig.getSecondUserAvatarSaveFilePath();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bm = BitmapFactory.decodeFile(avatarPath, options);
                if(bm != null)
                    rivAvatar.setImageBitmap(bm);

                // set texts
                tvUserName.setText(ui.username);
                tvNickyName.setText(ui.nickyname);
                tvScore.setText(Integer.toString(ui.score));
                tvExperience.setText(Integer.toString(ui.experience));
                tvRank.setText(ui.rank);
                tvLogout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new MaterialDialog.Builder(UserInfoActivity.this)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        super.onPositive(dialog);
                                        AsyncLogout al = new AsyncLogout();
                                        al.execute();
                                    }
                                })
                                .theme(Theme.LIGHT)
                                .titleColorRes(R.color.default_text_color_black)
                                .backgroundColorRes(R.color.dlgBackgroundColor)
                                .contentColorRes(R.color.dlgContentColor)
                                .positiveColorRes(R.color.dlgPositiveButtonColor)
                                .negativeColorRes(R.color.dlgNegativeButtonColor)
                                .content(R.string.dialog_content_sure_to_logout)
                                .contentGravity(GravityEnum.CENTER)
                                .positiveText(R.string.dialog_positive_ok)
                                .negativeText(R.string.dialog_negative_biao)
                                .show();
                    }
                });
            }
            else {
                Toast.makeText(UserInfoActivity.this, errorCode.toString(), Toast.LENGTH_SHORT).show();
                UserInfoActivity.this.finish(); // end dialog
            }
        }
    }

    private class AsyncLogout extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        MaterialDialog md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            md = new MaterialDialog.Builder(UserInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .content(R.string.system_fetching)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {

            byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserLogoutParams());
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
                LightUserSession.logOut();
                Toast.makeText(UserInfoActivity.this, "Logged out!", Toast.LENGTH_SHORT).show();
            }
            else
                Toast.makeText(UserInfoActivity.this, errorCode.toString(), Toast.LENGTH_SHORT).show();

            // terminate this activity
            md.dismiss();
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
}
