package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightUserSession;

import java.io.ByteArrayOutputStream;

/**
 * Created by MewX on 2015/6/12.
 * User Login Activity.
 */
public class UserLoginActivity extends AppCompatActivity {

    // private vars
    private EditText etUserName = null;
    private EditText etPassword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_user_login);

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
        etUserName = (EditText)findViewById(R.id.edit_username);
        etPassword = (EditText)findViewById(R.id.edit_password);
        TextView tvLogin = (TextView)findViewById(R.id.btn_login);
        TextView tvRegister = (TextView)findViewById(R.id.btn_register);

        // listeners
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(etUserName.getText().toString().length() == 0 || etUserName.getText().toString().length() > 30
                        || etPassword.getText().toString().length() == 0 || etPassword.getText().toString().length() > 30) {
                    Toast.makeText(UserLoginActivity.this, getResources().getString(R.string.system_info_fill_not_complete), Toast.LENGTH_SHORT).show();
                    return;
                }

                // async login
                AsyncLoginTask alt = new AsyncLoginTask();
                alt.execute(etUserName.getText().toString(), etPassword.getText().toString());
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(UserLoginActivity.this)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                // use default browser
//                                Uri uri = Uri.parse(Wenku8API.RegisterURL);
//                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
//                                startActivity(intent);

                                // show browser list
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(Wenku8API.RegisterURL));
                                String title = getResources().getString(R.string.system_choose_browser);
                                Intent chooser = Intent.createChooser(intent, title);
                                startActivity(chooser);

                            }
                        })
                        .theme(Theme.LIGHT)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .negativeColorRes(R.color.dlgNegativeButtonColor)
                        .content(R.string.dialog_content_verify_register)
                        .contentGravity(GravityEnum.CENTER)
                        .positiveText(R.string.dialog_positive_ok)
                        .negativeText(R.string.dialog_negative_pass)
                        .show();
            }
        });
    }

    private class AsyncLoginTask extends AsyncTask<String, Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md = null;
        private Wenku8Error.ErrorCode we = Wenku8Error.ErrorCode.ERROR_DEFAULT;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(UserLoginActivity.this)
                    .theme(Theme.LIGHT)
                    .content(R.string.system_logging_in)
                    .progress(true, 0)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(String[] params) {
            // sleep to show dialog
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            we = LightUserSession.doLoginFromGiven(params[0], params[1]);
            if(we == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                // fetch avatar
                byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserAvatar());
                if(b == null) {
                    Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_noavatar);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    if(!LightCache.saveFile(GlobalConfig.getFirstUserAvatarSaveFilePath(), baos.toByteArray(), true))
                        LightCache.saveFile(GlobalConfig.getSecondUserAvatarSaveFilePath(), baos.toByteArray(), true);
                }
                else {
                    if(!LightCache.saveFile(GlobalConfig.getFirstUserAvatarSaveFilePath(), b, true))
                        LightCache.saveFile(GlobalConfig.getSecondUserAvatarSaveFilePath(), b, true);
                }
            }

            return we;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode i) {
            super.onPostExecute(i);

            md.dismiss();
            //  Toast.makeText(MyApp.getContext(),"session=" + LightUserSession.getSession(), Toast.LENGTH_SHORT).show();
            switch(i) {
                case SYSTEM_1_SUCCEEDED:
                    Toast.makeText(MyApp.getContext(), getResources().getString(R.string.system_logged), Toast.LENGTH_SHORT).show();
                    UserLoginActivity.this.finish();
                    break;

                case SYSTEM_2_ERROR_USERNAME:
                    Toast.makeText(MyApp.getContext(), getResources().getString(R.string.system_username_error), Toast.LENGTH_SHORT).show();
                    break;

                case SYSTEM_3_ERROR_PASSWORD:
                    Toast.makeText(MyApp.getContext(), getResources().getString(R.string.system_password_error), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
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
