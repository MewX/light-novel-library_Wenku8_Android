package org.mewx.wenku8.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.StackingBehavior;
import com.afollestad.materialdialogs.Theme;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.makeramen.roundedimageview.RoundedImageView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.UserInfoActivity;
import org.mewx.wenku8.activity.UserLoginActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.LightUserSession;

public class NavigationDrawerFragment extends Fragment {
    private static final String TAG = NavigationDrawerFragment.class.getSimpleName();

    private FirebaseAnalytics mFirebaseAnalytics;
    private View mFragmentContainerView;
    private ImageView bgImage;
    private MainActivity mainActivity = null;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private TextView tvUserName;
    private RoundedImageView rivUserAvatar;
    private boolean fakeDarkSwitcher = false;

    public NavigationDrawerFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_main_menu, container, false);
    }

    private View.OnClickListener generateNavigationButtonOnClickListener(
            @NonNull MainActivity.FragmentMenuOption targetFragment, @NonNull Fragment fragment) {
        return v -> {
            if (mainActivity.getCurrentFragment() == targetFragment) {
                // Already selected, so just ignore.
                return;
            }
            clearAllButtonColor();
            setHighLightButton(targetFragment);
            mainActivity.setCurrentFragment(targetFragment);
            mainActivity.changeFragment(fragment);
            closeDrawer();

            // Analysis.
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, fragment.getClass().getSimpleName());
            bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, fragment.getClass().getSimpleName());
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
        };
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set button clicked listener, mainly working on change fragment in MainActivity.
        try {
            mainActivity.findViewById(R.id.main_menu_rklist).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.RKLIST, new RKListFragment())
            );
            mainActivity.findViewById(R.id.main_menu_latest).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.LATEST, new LatestFragment())
            );
            mainActivity.findViewById(R.id.main_menu_fav).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.FAV, new FavFragment())
            );
            mainActivity.findViewById(R.id.main_menu_config).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.CONFIG, new ConfigFragment())
            );

            mainActivity.findViewById(R.id.main_menu_open_source).setOnClickListener(v -> {
                        FragmentActivity fragmentActivity = getActivity();
                        if (fragmentActivity == null) return;
                        new MaterialDialog.Builder(fragmentActivity)
                                .theme(Theme.LIGHT)
                                .title(R.string.main_menu_statement)
                                .content(GlobalConfig.getOpensourceLicense())
                                .stackingBehavior(StackingBehavior.ALWAYS)
                                .positiveColorRes(R.color.dlgPositiveButtonColor)
                                .positiveText(R.string.dialog_positive_known)
                                .show();
                    }
            );

            mainActivity.findViewById(R.id.main_menu_dark_mode_switcher).setOnClickListener(v -> openOrCloseDarkMode());

        } catch (NullPointerException e) {
            Toast.makeText(mainActivity, "NullPointerException in onActivityCreated();", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // User Account
        FragmentActivity activity = getActivity();
        if (activity != null) {
            rivUserAvatar = activity.findViewById(R.id.user_avatar);
            tvUserName = activity.findViewById(R.id.user_name);
        }

        View.OnClickListener ocl = v -> {
            if(!LightUserSession.getLogStatus() && GlobalConfig.isNetworkAvailable(getActivity())) {
                if(!LightUserSession.isUserInfoSet()) {
                    Intent intent = new Intent(getActivity(), UserLoginActivity.class);
                    startActivity(intent);
                }
                else {
                    // show dialog to login, error to jump to login activity
                    if(LightUserSession.aiui.getStatus() == AsyncTask.Status.FINISHED) {
                        Toast.makeText(getActivity(), "Relogged.", Toast.LENGTH_SHORT).show();
                        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
                        LightUserSession.aiui.execute();
                    }
                }
            }
            else if(!GlobalConfig.isNetworkAvailable(getActivity())) {
                // no network, no log in
                Toast.makeText(getActivity(), getResources().getString(R.string.system_network_error), Toast.LENGTH_SHORT).show();
            }
            else {
                // Logged, click to info page
                Intent intent = new Intent(getActivity(), UserInfoActivity.class);
                startActivity(intent);
            }
        };
        rivUserAvatar.setOnClickListener(ocl);
        tvUserName.setOnClickListener(ocl);

        // Initial: set color states here ...
        // get net work status, no net -> FAV
        if(activity != null && !GlobalConfig.isNetworkAvailable(activity)) {
            clearAllButtonColor();
            setHighLightButton(MainActivity.FragmentMenuOption.FAV);
            mainActivity.setCurrentFragment(MainActivity.FragmentMenuOption.FAV);
            mainActivity.changeFragment(new FavFragment());
        }
        else {
            clearAllButtonColor();
            setHighLightButton(mainActivity.getCurrentFragment());
            mainActivity.changeFragment(new LatestFragment());
        }
        // TODO: need to track the initial fragment.

        // set menu background
        if (activity != null) {
            bgImage = activity.findViewById(R.id.bg_img);
            updateMenuBackground();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setup(int fragmentId, DrawerLayout drawerLayout, Toolbar toolbar) {
        // get MainActivity
        mainActivity = (MainActivity) getActivity();
        if (mainActivity == null)
            Toast.makeText(getActivity(), "mainActivity == null !!! in setup()", Toast.LENGTH_SHORT).show();

        // Init Firebase Analytics on GA4.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(mainActivity);

        mFragmentContainerView = mainActivity.findViewById(fragmentId);
        mDrawerLayout = drawerLayout;
        mActionBarDrawerToggle = new ActionBarDrawerToggle(mainActivity, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) return;

                mainActivity.invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) return;

                mainActivity.invalidateOptionsMenu();
                updateNavigationBar();
            }
        };

        mDrawerLayout.post(() -> mActionBarDrawerToggle.syncState());
        mDrawerLayout.addDrawerListener(mActionBarDrawerToggle);
        updateNavigationBar();
    }

    private void clearOneButtonColor(int iconId, int textId, int backgroundId) {
        // Clear icon color.
        ImageButton imageButton = mainActivity.findViewById(iconId);
        if (imageButton != null) {
            imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
        }

        // Clear text color.
        TextView textView = mainActivity.findViewById(textId);
        if (textView != null) {
            textView.setTextColor(getResources().getColor(R.color.menu_text_color));
        }

        // Clear view background color (only works for API 16+).
        TableRow tableRow = mainActivity.findViewById(backgroundId);
        if (tableRow != null) {
            tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
        }
    }

    /**
     * This function clear all the effects on button, and it needs API Level 16.
     * So if the device is between 4.0-4.1, it will appear no effects.
     *
     * Notice:
     * Once the enum MainActivity.FRAGMENT_LIST changes, this function show be edited.
     */
    private void clearAllButtonColor() {
        clearOneButtonColor(R.id.main_menu_ic_rklist, R.id.main_menu_text_rklist, R.id.main_menu_rklist);
        clearOneButtonColor(R.id.main_menu_ic_latest, R.id.main_menu_text_latest, R.id.main_menu_latest);
        clearOneButtonColor(R.id.main_menu_ic_fav, R.id.main_menu_text_fav, R.id.main_menu_fav);
        clearOneButtonColor(R.id.main_menu_ic_config, R.id.main_menu_text_config, R.id.main_menu_config);
    }

    @SuppressLint("NewApi")
    private void setHighLightButton(int iconId, int textId, int backgroundId) {
        ImageButton icon = mainActivity.findViewById(iconId);
        if (icon != null) {
            icon.setColorFilter(getResources().getColor(R.color.menu_text_color_selected));
        }

        TextView textView = mainActivity.findViewById(textId);
        if (textView != null) {
            textView.setTextColor(getResources().getColor(R.color.menu_item_white));
        }

        // Set view background color (only works for API 16+).
        TableRow tableRow = mainActivity.findViewById(backgroundId);
        if (tableRow != null) {
            tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item_selected));
        }
    }

    /**
     * This function will highlight the button selected, and switch to the fragment in MainActivity.
     *
     * @param f the target fragment, type MainActivity.FRAGMENT_LIST.
     */
    private void setHighLightButton(MainActivity.FragmentMenuOption f) {
        switch (f) {
            case RKLIST:
                setHighLightButton(R.id.main_menu_ic_rklist, R.id.main_menu_text_rklist, R.id.main_menu_rklist);
                break;

            case LATEST:
                setHighLightButton(R.id.main_menu_ic_latest, R.id.main_menu_text_latest, R.id.main_menu_latest);
                break;

            case FAV:
                setHighLightButton(R.id.main_menu_ic_fav, R.id.main_menu_text_fav, R.id.main_menu_fav);
                break;

            case CONFIG:
                setHighLightButton(R.id.main_menu_ic_config, R.id.main_menu_text_config, R.id.main_menu_config);
                break;
        }
    }

    /**
     * Judge whether the dark mode is open. If is open, close it; else open it.
     */
    private void openOrCloseDarkMode() {
        TextView darkModeSwitcherText = mainActivity.findViewById(R.id.main_menu_dark_mode_switcher);
        if (darkModeSwitcherText != null) {
            // Set view background color (only works for API 16+).
            darkModeSwitcherText.setTextColor(getResources().getColor(
                    fakeDarkSwitcher ?/*switch off*/ R.color.menu_text_color :/*switch on*/ R.color.menu_text_color_selected
            ));
            darkModeSwitcherText.setBackground(getResources().getDrawable(
                    fakeDarkSwitcher ?/*switch off*/R.drawable.btn_menu_item :/*switch on*/R.drawable.btn_menu_item_selected
            ));
        }

        fakeDarkSwitcher = !fakeDarkSwitcher;
        Toast.makeText(getActivity(), "夜间模式到阅读界面去试试~", Toast.LENGTH_SHORT).show();
    }

    private void updateNavigationBar() {
        if (Build.VERSION.SDK_INT < 19) {
            // Transparency is not supported in below KitKat.
            return;
        }

        // test navigation bar exist
        FragmentActivity activity = getActivity();
        Point navBar = LightTool.getNavigationBarSize(getActivity());

        // TODO: fix this margin for screen cutout.
        LinearLayout ll = mainActivity.findViewById(R.id.main_menu_bottom_layout);
        if (activity != null && navBar.y == 0) {
            ll.setPadding(0, 0, 0, 0); // hide
        }
        else if (activity != null && (navBar.y < 10 || navBar.y >= LightTool.getAppUsableScreenSize(activity).y)) {
            ll.setPadding(0, 0, 0, LightTool.getAppUsableScreenSize(activity).y / 10);
        }
        else {
            ll.setPadding(0, 0, 0, navBar.y); // show
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // user info update
        if(LightUserSession.isUserInfoSet() && !tvUserName.getText().toString().equals(LightUserSession.getUsernameOrEmail())
                && (LightCache.testFileExist(GlobalConfig.getFirstUserAvatarSaveFilePath())
                || LightCache.testFileExist(GlobalConfig.getSecondUserAvatarSaveFilePath()))) {
            tvUserName.setText(LightUserSession.getUsernameOrEmail());

            String avatarPath;
            if(LightCache.testFileExist(GlobalConfig.getFirstUserAvatarSaveFilePath()))
                avatarPath = GlobalConfig.getFirstUserAvatarSaveFilePath();
            else
                avatarPath = GlobalConfig.getSecondUserAvatarSaveFilePath();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeFile(avatarPath, options);
            if(bm != null)
                rivUserAvatar.setImageBitmap(bm);
        }
        else if(!LightUserSession.isUserInfoSet()) {
            tvUserName.setText(getResources().getString(R.string.main_menu_not_login));
            rivUserAvatar.setImageDrawable(getResources().getDrawable(R.drawable.ic_noavatar));
        }

        // update menu background
        updateMenuBackground();
    }

    private void updateMenuBackground() {
        String settingMenuBgId = GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_id);
        if(settingMenuBgId != null) {
            switch (settingMenuBgId) {
                case "0":
                    Bitmap bmMenuBackground;
                    try {
                        bmMenuBackground = BitmapFactory.decodeFile(GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_path));
                    } catch (OutOfMemoryError oome) {
                        try {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 2;
                            bmMenuBackground = BitmapFactory.decodeFile(GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_path), options);
                            if(bmMenuBackground == null) throw new Exception("PictureLoadFailureException");
                        } catch(Exception e) {
                            e.printStackTrace();
                            Toast.makeText(getActivity(), "Exception: " + e.toString() + "\n可能的原因有：图片不在内置SD卡；图片格式不正确；图片像素尺寸太大，请使用小一点的图，谢谢，此功能为试验性功能；", Toast.LENGTH_SHORT).show();
                            bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_04));
                            return;
                        }
                    }
                    bgImage.setImageBitmap(bmMenuBackground);
                    break;
                case "1":
                    bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_01));
                    break;
                case "2":
                    bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_02));
                    break;
                case "3":
                    bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_03));
                    break;
                case "4":
                    bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_04));
                    break;
                case "5":
                    bgImage.setImageDrawable(getResources().getDrawable(R.drawable.bg_avatar_05));
                    break;
            }
        }
    }

    public void openDrawer() {
        mDrawerLayout.openDrawer(mFragmentContainerView);
    }

    public void closeDrawer() {
        mDrawerLayout.closeDrawer(mFragmentContainerView);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

}
