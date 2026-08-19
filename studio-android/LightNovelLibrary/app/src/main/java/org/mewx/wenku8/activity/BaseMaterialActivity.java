package org.mewx.wenku8.activity;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.mewx.wenku8.R;
import org.mewx.wenku8.network.LightUserSession;
import org.mewx.wenku8.util.CrashReporter;

/**
 * The base activity that handles Material Design style status bar or so.
 */
public class BaseMaterialActivity extends AppCompatActivity {
    protected enum HomeIndicatorStyle {
        NONE, // TODO: implement when using this style.
        HAMBURGER,
        ARROW,
    }

    protected enum StatusBarColor {
        PRIMARY,
        WHITE,
        DARK,
    }

    private Toolbar toolbar;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentBreadcrumbs =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    CrashReporter.log(f.getClass().getSimpleName() + "#onResume");
                }

                @Override
                public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    // The interesting one: an AsyncTask finishing after this point is exactly the
                    // "touched a view on a detached Fragment" crash.
                    CrashReporter.log(f.getClass().getSimpleName() + "#onDetach");
                }
            };

    public BaseMaterialActivity() {
        super();
    }

    /**
     * Breadcrumbs for every Activity that inherits from this one. The point is that a crash
     * report shows the screen sequence that led to it, and in particular whether the Activity
     * was being recreated (savedInstanceState != null) -- the rotation and process-death paths
     * are the ones most likely to be at fault.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashReporter.setScreen(getClass().getSimpleName(),
                savedInstanceState == null ? "onCreate" : "onCreate(restored)");

        // One registration covers every Fragment this Activity hosts, child fragments included,
        // which beats copying an onResume() override into each of them and means Fragments added
        // later are traced for free. Fragment attach/detach matters here because most of the
        // background loading -- and therefore most of the lifecycle-related crashes -- happens in
        // Fragments rather than in the Activity.
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(fragmentBreadcrumbs, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CrashReporter.setScreen(getClass().getSimpleName(), "onResume");
        // Refreshed here rather than at each login/logout call site: the session is static state
        // mutated from several places including a background heartbeat, and sampling it on every
        // screen entry cannot fall out of sync the way four separate hooks would.
        CrashReporter.setLoggedIn(LightUserSession.getLogStatus());
    }

    protected Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = findViewById(R.id.toolbar_actionbar);
        }
        return toolbar;
    }

    /**
     * Sets the status bar color to black with the given alpha (0.0 = transparent, 1.0 = opaque).
     */
    protected void setStatusBarAlpha(float alpha) {
        getWindow().setStatusBarColor(Color.argb((int) (alpha * 255), 0, 0, 0));
    }

    /**
     * Sets the navigation bar color to black with the given alpha (0.0 = transparent, 1.0 = opaque).
     */
    protected void setNavigationBarAlpha(float alpha) {
        getWindow().setNavigationBarColor(Color.argb((int) (alpha * 255), 0, 0, 0));
    }

    protected void initMaterialStyle(int layoutId) {
        initMaterialStyle(layoutId, HomeIndicatorStyle.ARROW);
    }

    protected void initMaterialStyle(int layoutId, HomeIndicatorStyle indicatorStyle) {
        initMaterialStyle(layoutId, StatusBarColor.PRIMARY, indicatorStyle);
    }

    protected void initMaterialStyle(int layoutId, StatusBarColor statusBarColor) {
        initMaterialStyle(layoutId, statusBarColor, HomeIndicatorStyle.ARROW);
    }

    protected void initMaterialStyle(int layoutId, StatusBarColor statusBarColor, HomeIndicatorStyle indicatorStyle) {
        setContentView(layoutId);

        // set indicator enable
        if (getToolbar() != null) {
            setSupportActionBar(getToolbar());
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

            // Default indicator is hamburger.
            if (indicatorStyle == HomeIndicatorStyle.ARROW) {
                final Drawable upArrow = ContextCompat.getDrawable(this, R.drawable.ic_svg_back);
                if (upArrow != null) {
                    upArrow.setColorFilter(ContextCompat.getColor(this, R.color.default_white), PorterDuff.Mode.SRC_ATOP);
                }
                getSupportActionBar().setHomeAsUpIndicator(upArrow);
            }
        }

        // Set status bar color with a black tint overlay.
        float statusBarAlpha = statusBarColor == StatusBarColor.DARK ? 0.9f : 0.15f;
        setStatusBarAlpha(statusBarAlpha);

        // Set navigation bar color.
        if (statusBarColor == StatusBarColor.DARK) {
            setNavigationBarAlpha(0.8f);
        } else {
            final int navBarColorId = statusBarColor == StatusBarColor.PRIMARY ?
                    R.color.myNavigationColor : R.color.myNavigationColorWhite;
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, navBarColorId));
        }
    }

}
