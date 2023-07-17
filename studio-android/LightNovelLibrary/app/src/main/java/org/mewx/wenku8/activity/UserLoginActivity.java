package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

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
public class UserLoginActivity extends BaseMaterialActivity {

    // private vars
    private EditText etUserNameOrEmail = null;
    private EditText etPassword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_user_login);

        // get views
        etUserNameOrEmail = findViewById(R.id.edit_username_or_email);
        etPassword = findViewById(R.id.edit_password);
        TextView tvLogin = findViewById(R.id.btn_login);
        TextView tvRegister = findViewById(R.id.btn_register);

        // listeners
        tvLogin.setOnClickListener(v -> {
            if(etUserNameOrEmail.getText().toString().length() == 0 || etUserNameOrEmail.getText().toString().length() > 30
                    || etPassword.getText().toString().length() == 0 || etPassword.getText().toString().length() > 30) {
                Toast.makeText(UserLoginActivity.this, getResources().getString(R.string.system_info_fill_not_complete), Toast.LENGTH_SHORT).show();
                return;
            }

            // async login
            AsyncLoginTask alt = new AsyncLoginTask();
            alt.execute(etUserNameOrEmail.getText().toString(), etPassword.getText().toString());
        });

        tvRegister.setOnClickListener(v -> new MaterialDialog.Builder(UserLoginActivity.this)
                .onPositive((dialog, which) -> {
                    // show browser list
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(Wenku8API.REGISTER_URL));
                    String title = getResources().getString(R.string.system_choose_browser);
                    Intent chooser = Intent.createChooser(intent, title);
                    startActivity(chooser);
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
                .show());
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
                byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getUserAvatar());
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
}
