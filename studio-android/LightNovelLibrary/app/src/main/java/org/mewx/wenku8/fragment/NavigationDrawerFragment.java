package org.mewx.wenku8.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.makeramen.roundedimageview.RoundedImageView;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.UserInfoActivity;
import org.mewx.wenku8.activity.UserLoginActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.LightUserSession;

//@TargetApi(16)
public class NavigationDrawerFragment extends Fragment {
    private final String TAG = "NavigationDrawerFragment";
    private View mFragmentContainerView;
    private ImageView bgImage;
    private MainActivity mainActivity = null;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private TextView tvUserName;
    private RoundedImageView rivUserAvatar;
    private Bitmap bmMenuBackground;
    private boolean fakeDarkSwitcher = false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_main_menu, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set button clicked listener, mainly working on change fragment in MainActivity.
        try {
            mainActivity.findViewById(R.id.main_menu_rklist).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mainActivity.getCurrentFragment() == MainActivity.FRAGMENT_LIST.RKLIST)
                                return; // selected button, so just ignore.
                            clearAllButtonColor();
                            setHighLightButton(MainActivity.FRAGMENT_LIST.RKLIST);
                            mainActivity.setCurrentFragment(MainActivity.FRAGMENT_LIST.RKLIST);
                            mainActivity.changeFragment(new RKListFragment());
                            closeDrawer();
                        }
                    }
            );

            mainActivity.findViewById(R.id.main_menu_latest).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mainActivity.getCurrentFragment() == MainActivity.FRAGMENT_LIST.LATEST)
                                return; // selected button, so just ignore.
                            clearAllButtonColor();
                            setHighLightButton(MainActivity.FRAGMENT_LIST.LATEST);
                            mainActivity.setCurrentFragment(MainActivity.FRAGMENT_LIST.LATEST);
                            mainActivity.changeFragment(new LatestFragment());
                            closeDrawer();
                        }
                    }
            );

            mainActivity.findViewById(R.id.main_menu_fav).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mainActivity.getCurrentFragment() == MainActivity.FRAGMENT_LIST.FAV)
                                return; // selected button, so just ignore.
                            clearAllButtonColor();
                            setHighLightButton(MainActivity.FRAGMENT_LIST.FAV);
                            mainActivity.setCurrentFragment(MainActivity.FRAGMENT_LIST.FAV);
                            mainActivity.changeFragment(new FavFragment());
                            closeDrawer();
                        }
                    }
            );

            mainActivity.findViewById(R.id.main_menu_config).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mainActivity.getCurrentFragment() == MainActivity.FRAGMENT_LIST.CONFIG)
                                return; // selected button, so just ignore.
                            clearAllButtonColor();
                            setHighLightButton(MainActivity.FRAGMENT_LIST.CONFIG);
                            mainActivity.setCurrentFragment(MainActivity.FRAGMENT_LIST.CONFIG);
                            mainActivity.changeFragment(new ConfigFragment());
                            closeDrawer();
                        }
                    }
            );

            mainActivity.findViewById(R.id.main_menu_open_source).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new MaterialDialog.Builder(getActivity())
                                    .theme(Theme.LIGHT)
                                    .title(R.string.main_menu_statement)
                                    .content(GlobalConfig.getOpensourceLicense())
                                    .forceStacking(true)
                                    .positiveColorRes(R.color.dlgPositiveButtonColor)
                                    .positiveText(R.string.dialog_positive_known)
                                    .show();
                        }
                    }
            );

            mainActivity.findViewById(R.id.main_menu_dark_mode_switcher).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openOrCloseDarkMode();
                        }
                    }
            );

        } catch (NullPointerException e) {
            Toast.makeText(mainActivity, "NullPointerException in onActivityCreated();", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // User Account
//        RelativeLayout rlBackground = (RelativeLayout)getActivity().findViewById(R.id.top_background);
        rivUserAvatar = (RoundedImageView)getActivity().findViewById(R.id.user_avatar);
        tvUserName = (TextView)getActivity().findViewById(R.id.user_name);

        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            }
        };
        rivUserAvatar.setOnClickListener(ocl);
        tvUserName.setOnClickListener(ocl);

        // Initial: set color states here ...
        // get net work status, no net -> FAV
        if(!GlobalConfig.isNetworkAvailable(getActivity())) {
            clearAllButtonColor();
            setHighLightButton(MainActivity.FRAGMENT_LIST.FAV);
            mainActivity.setCurrentFragment(MainActivity.FRAGMENT_LIST.FAV);
            mainActivity.changeFragment(new FavFragment());
        }
        else {
            clearAllButtonColor();
            setHighLightButton(mainActivity.getCurrentFragment());
            mainActivity.changeFragment(new LatestFragment());
        }

        // set menu background
        bgImage = (ImageView) getActivity().findViewById(R.id.bg_img);
        if(GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_id) != null) {
            switch (GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_id)) {
                case "0":
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

//    public ActionBarDrawerToggle getActionBarDrawerToggle() {
//        return mActionBarDrawerToggle;
//    }
//
//    public void setActionBarDrawerToggle(ActionBarDrawerToggle actionBarDrawerToggle) {
//        mActionBarDrawerToggle = actionBarDrawerToggle;
//    }

    public void setup(int fragmentId, DrawerLayout drawerLayout, Toolbar toolbar) {
        // get MainActivity
        mainActivity = (MainActivity) getActivity();
        if (mainActivity == null)
            Toast.makeText(getActivity(), "mainActivity == null !!! in setup()", Toast.LENGTH_SHORT).show();

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

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mActionBarDrawerToggle.syncState();
            }
        });
        mDrawerLayout.setDrawerListener(mActionBarDrawerToggle);
        updateNavigationBar();
    }

    /**
     * This function clear all the effects on button, and it needs API Level 16.
     * So if the device is between 4.0-4.1, it will appear no effects.
     *
     * Notice:
     * Once the enum MainActivity.FRAGMENT_LIST changes, this function show be edited.
     */
    private void clearAllButtonColor() {
        // sdk ver is too low
        if (Build.VERSION.SDK_INT < 16) {
            try {
                // Clear icon color
                ImageButton imageButton;
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_rklist);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_latest);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_fav);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_config);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));

                // Clear icon color effects
                TextView textView;
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_rklist);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_latest);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_fav);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_config);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in clearAllButtonColor(); sdk16-", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            try {
                // Clear the background effects on "main buttons"
                TableRow tableRow;
                tableRow = (TableRow) mainActivity.findViewById(R.id.main_menu_rklist);
                tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
                tableRow = (TableRow) mainActivity.findViewById(R.id.main_menu_latest);
                tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
                tableRow = (TableRow) mainActivity.findViewById(R.id.main_menu_fav);
                tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
                tableRow = (TableRow) mainActivity.findViewById(R.id.main_menu_config);
                tableRow.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));

                // Clear icon color effects
                TextView textView;
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_rklist);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_latest);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_fav);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));
                textView = (TextView) mainActivity.findViewById(R.id.main_menu_text_config);
                textView.setTextColor(getResources().getColor(R.color.menu_text_color));

                // Clear icon color
                ImageButton imageButton;
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_rklist);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_latest);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_fav);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));
                imageButton = (ImageButton) mainActivity.findViewById(R.id.main_menu_ic_config);
                imageButton.setColorFilter(getResources().getColor(R.color.menu_text_color));

                // Clear the dark and light switcher button effect
//            textView = (TextView) mainActivity.findViewById(R.id.main_menu_dark_mode_switcher);
//            textView.setTextColor(getResources().getColor(R.color.main_menu_color_text));
//            textView.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));

            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in clearAllButtonColor();", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    /**
     * This function will highlight the button selected, and switch to the fragment in MainActivity.
     *
     * @param f the target fragment, type MainActivity.FRAGMENT_LIST.
     */
    private void setHighLightButton(MainActivity.FRAGMENT_LIST f) {
        // sdk ver is too low
        if (Build.VERSION.SDK_INT < 16) {
            try {
                switch (f) {
                    case RKLIST:
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_rklist)).setTextColor(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_rklist)).setColorFilter(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        break;

                    case LATEST:
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_latest)).setTextColor(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_latest)).setColorFilter(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        break;

                    case FAV:
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_fav)).setTextColor(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_fav)).setColorFilter(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        break;

                    case CONFIG:
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_config)).setTextColor(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_config)).setColorFilter(
                                getResources().getColor(R.color.menu_item_blue)
                        );
                        break;

                }
            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in setHighLightButton(); sdk16-", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            try {
                switch (f) {
                    case RKLIST:
                        mainActivity.findViewById(R.id.main_menu_rklist).setBackground(
                                getResources().getDrawable(R.drawable.btn_menu_item_selected)
                        );
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_rklist)).setTextColor(
                                getResources().getColor(R.color.menu_text_color_selected)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_rklist)).setColorFilter(
                                getResources().getColor(R.color.menu_item_white)
                        );
                        break;

                    case LATEST:
                        mainActivity.findViewById(R.id.main_menu_latest).setBackground(
                                getResources().getDrawable(R.drawable.btn_menu_item_selected)
                        );
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_latest)).setTextColor(
                                getResources().getColor(R.color.menu_text_color_selected)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_latest)).setColorFilter(
                                getResources().getColor(R.color.menu_item_white)
                        );
                        break;

                    case FAV:
                        mainActivity.findViewById(R.id.main_menu_fav).setBackground(
                                getResources().getDrawable(R.drawable.btn_menu_item_selected)
                        );
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_fav)).setTextColor(
                                getResources().getColor(R.color.menu_text_color_selected)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_fav)).setColorFilter(
                                getResources().getColor(R.color.menu_item_white)
                        );
                        break;

                    case CONFIG:
                        mainActivity.findViewById(R.id.main_menu_config).setBackground(
                                getResources().getDrawable(R.drawable.btn_menu_item_selected)
                        );
                        ((TextView) mainActivity.findViewById(R.id.main_menu_text_config)).setTextColor(
                                getResources().getColor(R.color.menu_text_color_selected)
                        );
                        ((ImageButton) mainActivity.findViewById(R.id.main_menu_ic_config)).setColorFilter(
                                getResources().getColor(R.color.menu_item_white)
                        );
                        break;

                }
            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in setHighLightButton();", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    /**
     * Judge whether the dark mode is open. If is open, close it; else open it.
     */
    private void openOrCloseDarkMode() {
        // sdk ver is too low
        if (Build.VERSION.SDK_INT < 16) {
            try {
                if (fakeDarkSwitcher) {
                    // In dark mode, so close it
                    ((TextView) mainActivity.findViewById(R.id.main_menu_dark_mode_switcher)).setTextColor(
                            getResources().getColor(R.color.menu_text_color)
                    );
                } else {
                    // In light mode, so open it
                    ((TextView) mainActivity.findViewById(R.id.main_menu_dark_mode_switcher)).setTextColor(
                            getResources().getColor(R.color.menu_item_blue)
                    );
                }
            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in openOrCloseDarkMode(); sdk16-", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            try {
                if (fakeDarkSwitcher) {
                    // In dark mode, so close it
                    ((TextView) mainActivity.findViewById(R.id.main_menu_dark_mode_switcher)).setTextColor(
                            getResources().getColor(R.color.menu_text_color)
                    );
                    mainActivity.findViewById(R.id.main_menu_dark_mode_switcher).setBackground(
                            getResources().getDrawable(R.drawable.btn_menu_item)
                    );
                } else {
                    // In light mode, so open it
                    ((TextView) mainActivity.findViewById(R.id.main_menu_dark_mode_switcher)).setTextColor(
                            getResources().getColor(R.color.menu_text_color_selected)
                    );
                    mainActivity.findViewById(R.id.main_menu_dark_mode_switcher).setBackground(
                            getResources().getDrawable(R.drawable.btn_menu_item_selected)
                    );
                }
            } catch (NullPointerException e) {
                Toast.makeText(mainActivity, "NullPointerException in openOrCloseDarkMode();", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }

        fakeDarkSwitcher = !fakeDarkSwitcher;
        Toast.makeText(getActivity(), "夜间模式到阅读界面去试试~", Toast.LENGTH_SHORT).show();
    }

    private void updateNavigationBar() {
        // test navigation bar exist
        Point navBar = LightTool.getNavigationBarSize(getActivity());
//                Toast.makeText(getActivity(), "width = " + navBar.x + "; height = " + navBar.y, Toast.LENGTH_SHORT).show();
        LinearLayout ll = (LinearLayout)mainActivity.findViewById(R.id.main_menu_bottom_layout);
        if(navBar.y == 0)
            ll.setPadding(0, 0, 0, 0); // hide
        else if(navBar.y < 10 || navBar.y >= LightTool.getAppUsableScreenSize(getActivity()).y)
            ll.setPadding(0, 0, 0, LightTool.getAppUsableScreenSize(getActivity()).y / 10);
        else
            ll.setPadding(0, 0, 0, navBar.y); // show
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(TAG);

        // user info update
        if(LightUserSession.isUserInfoSet() && !tvUserName.getText().toString().equals(LightUserSession.getUsername())
                && (LightCache.testFileExist(GlobalConfig.getFirstUserAvatarSaveFilePath())
                || LightCache.testFileExist(GlobalConfig.getSecondUserAvatarSaveFilePath()))) {
            tvUserName.setText(LightUserSession.getUsername());

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
        if(GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_id) != null) {
            switch (GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_id)) {
                case "0":
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(TAG);
    }
}
