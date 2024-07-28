package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.mewx.wenku8.R;

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

    private SystemBarTintManager tintManager;
    private Toolbar toolbar;

    public BaseMaterialActivity() {
        super();
    }

    protected SystemBarTintManager getTintManager() {
        if (tintManager == null) {
            tintManager = new SystemBarTintManager(this);
        }
        return tintManager;
    }

    protected Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = findViewById(R.id.toolbar_actionbar);
        }
        return toolbar;
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
                final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
                if (upArrow != null) {
                    upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
                }
                getSupportActionBar().setHomeAsUpIndicator(upArrow);
            }
        }

        // change status bar color tint, and this require SDK16
        // Android API 22 has more effects on status bar, so ignore
        // create our manager instance after the content view is set
        tintManager = getTintManager();
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setNavigationBarTintEnabled(true);
        tintManager.setTintAlpha(statusBarColor == StatusBarColor.DARK ? 0.9f : 0.15f);
        tintManager.setNavigationBarAlpha(statusBarColor == StatusBarColor.DARK ? 0.8f : 0.0f);
        // set all color
        tintManager.setTintColor(getResources().getColor(android.R.color.black));

        // set Navigation bar color
        if (Build.VERSION.SDK_INT >= 21 && statusBarColor != StatusBarColor.DARK) {
            final int statusBarColorId = statusBarColor == StatusBarColor.PRIMARY ?
                    R.color.myNavigationColor : R.color.myNavigationColorWhite;
            getWindow().setNavigationBarColor(getResources().getColor(statusBarColorId));
        }
    }

}
