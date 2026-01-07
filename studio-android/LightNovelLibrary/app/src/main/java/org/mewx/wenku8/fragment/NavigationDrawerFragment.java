package org.mewx.wenku8.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RatingBar;
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
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.VideoOptions;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.makeramen.roundedimageview.RoundedImageView;

import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.UserInfoActivity;
import org.mewx.wenku8.activity.UserLoginActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.network.LightUserSession;

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
    private NativeAd mNativeAd;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Mobile Ads SDK
        MobileAds.initialize(getContext(), initializationStatus -> {});
        refreshAd();

        // Ensure mainActivity is initialized
        if (mainActivity == null && getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
        }

        // set button clicked listener, mainly working on change fragment in MainActivity.
        try {
            view.findViewById(R.id.main_menu_rklist).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.RKLIST, new RKListFragment())
            );
            view.findViewById(R.id.main_menu_latest).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.LATEST, new LatestFragment())
            );
            view.findViewById(R.id.main_menu_fav).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.FAV, new FavFragment())
            );
            view.findViewById(R.id.main_menu_config).setOnClickListener(
                    generateNavigationButtonOnClickListener(
                            MainActivity.FragmentMenuOption.CONFIG, new ConfigFragment())
            );

            view.findViewById(R.id.main_menu_open_source).setOnClickListener(v -> {
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

            view.findViewById(R.id.main_menu_dark_mode_switcher).setOnClickListener(v -> openOrCloseDarkMode());

        } catch (NullPointerException e) {
            Toast.makeText(getContext(), "NullPointerException in onViewCreated();", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // User Account
        rivUserAvatar = view.findViewById(R.id.user_avatar);
        tvUserName = view.findViewById(R.id.user_name);

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
                        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo(getContext(),/* failureCallback= */ () -> {
                            if (!LightCache.deleteFile(GlobalConfig.getFirstFullUserAccountSaveFilePath()))
                                LightCache.deleteFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
                            if (!LightCache.deleteFile(GlobalConfig.getFirstUserAvatarSaveFilePath()))
                                LightCache.deleteFile(GlobalConfig.getSecondUserAvatarSaveFilePath());
                            Toast.makeText(getContext(), getContext().getResources().getString(R.string.system_log_info_outofdate), Toast.LENGTH_SHORT).show();
                        }, GlobalConfig::loadUserInfoSet);
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
        if(getActivity() != null && !GlobalConfig.isNetworkAvailable(getActivity())) {
            clearAllButtonColor();
            setHighLightButton(MainActivity.FragmentMenuOption.FAV);
            if (mainActivity != null) {
                mainActivity.setCurrentFragment(MainActivity.FragmentMenuOption.FAV);
                mainActivity.changeFragment(new FavFragment());
            }
        }
        else {
            clearAllButtonColor();
            if (mainActivity != null) {
                setHighLightButton(mainActivity.getCurrentFragment());
                mainActivity.changeFragment(new LatestFragment());
            }
        }
        // TODO: need to track the initial fragment.

        // set menu background
        bgImage = view.findViewById(R.id.bg_img);
        updateMenuBackground();
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
        if (mainActivity == null) return;
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
        if (mainActivity == null) return;

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
        if (mainActivity == null) return;

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
        if (mainActivity == null) return;

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
        if (bgImage == null) return;
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
                            Toast.makeText(getActivity(), "Exception: " + e + "\n可能的原因有：图片不在内置SD卡；图片格式不正确；图片像素尺寸太大，请使用小一点的图，谢谢，此功能为试验性功能；", Toast.LENGTH_SHORT).show();
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

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void refreshAd() {
        AdLoader.Builder builder = new AdLoader.Builder(getContext(),
                BuildConfig.DEBUG ? "ca-app-pub-3940256099942544/2247696110" /* test ID */ :
                        "ca-app-pub-7333757578973883/7014476152" /* real ID */);
        builder.withNativeAdOptions(new NativeAdOptions.Builder()
                .setVideoOptions(new VideoOptions.Builder().setStartMuted(true).build())
                .build());

        builder.forNativeAd(nativeAd -> {
            // If this callback occurs after the activity is destroyed, we must destroy and return;
            // or we may get a memory leak.
            if (getActivity() == null) {
                nativeAd.destroy();
                return;
            }
            boolean isDestroyed = getActivity().isDestroyed();
            if (isDestroyed || getActivity().isFinishing() || getActivity().isChangingConfigurations()) {
                nativeAd.destroy();
                return;
            }
            // Must call destroy on old ads when you are done with them, otherwise memory leak.
            if (mNativeAd != null) {
                mNativeAd.destroy();
            }
            mNativeAd = nativeAd;

            View view = getView();
            if (view == null) return;
            FrameLayout frameLayout = view.findViewById(R.id.ad_container);

            if (frameLayout != null) {
                NativeAdView adView = (NativeAdView) getLayoutInflater().inflate(R.layout.ad_unified, null);
                populateNativeAdView(nativeAd, adView);
                frameLayout.removeAllViews();
                frameLayout.addView(adView);
            }
        });

        AdLoader adLoader = builder.withAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                // Handle the failure by logging, altering the UI, or else.
                Log.e(TAG,loadAdError.toString());
            }
        }).build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        // Set the media view.
        adView.setMediaView(adView.findViewById(R.id.ad_media));

        // Set other ad assets.
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setPriceView(adView.findViewById(R.id.ad_price));
        adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
        adView.setStoreView(adView.findViewById(R.id.ad_store));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

        // The headline and mediaContent are guaranteed to be in every NativeAd.
        ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        adView.getMediaView().setMediaContent(nativeAd.getMediaContent());

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before trying to display them.
        if (nativeAd.getBody() == null) {
            adView.getBodyView().setVisibility(View.INVISIBLE);
        } else {
            adView.getBodyView().setVisibility(View.VISIBLE);
            ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
        }

        if (nativeAd.getCallToAction() == null) {
            adView.getCallToActionView().setVisibility(View.INVISIBLE);
        } else {
            adView.getCallToActionView().setVisibility(View.VISIBLE);
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
        }

        if (nativeAd.getIcon() == null) {
            adView.getIconView().setVisibility(View.GONE);
        } else {
            ((ImageView) adView.getIconView()).setImageDrawable(
                    nativeAd.getIcon().getDrawable());
            adView.getIconView().setVisibility(View.VISIBLE);
        }

        if (nativeAd.getPrice() == null) {
            adView.getPriceView().setVisibility(View.INVISIBLE);
        } else {
            adView.getPriceView().setVisibility(View.VISIBLE);
            ((TextView) adView.getPriceView()).setText(nativeAd.getPrice());
        }

        if (nativeAd.getStore() == null) {
            adView.getStoreView().setVisibility(View.INVISIBLE);
        } else {
            adView.getStoreView().setVisibility(View.VISIBLE);
            ((TextView) adView.getStoreView()).setText(nativeAd.getStore());
        }

        if (nativeAd.getStarRating() == null) {
            adView.getStarRatingView().setVisibility(View.INVISIBLE);
        } else {
            ((RatingBar) adView.getStarRatingView())
                    .setRating(nativeAd.getStarRating().floatValue());
            adView.getStarRatingView().setVisibility(View.VISIBLE);
        }

        if (nativeAd.getAdvertiser() == null) {
            adView.getAdvertiserView().setVisibility(View.INVISIBLE);
        } else {
            ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
            adView.getAdvertiserView().setVisibility(View.VISIBLE);
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        adView.setNativeAd(nativeAd);
    }

    @Override
    public void onDestroy() {
        if (mNativeAd != null) {
            mNativeAd.destroy();
        }
        super.onDestroy();
    }

}
