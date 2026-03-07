package org.mewx.wenku8.activity;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

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

    private Toolbar toolbar;

    public BaseMaterialActivity() {
        super();
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
