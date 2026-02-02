package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;

import org.mewx.wenku8.R;

public class BaseMD3Activity extends AppCompatActivity {

    private Toolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    protected void initMaterialStyle(int layoutId) {
        setContentView(layoutId);

        toolbar = findViewById(R.id.toolbar_actionbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);

                // Use the same back arrow as BaseMaterialActivity
                final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
                if (upArrow != null) {
                    upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
                    getSupportActionBar().setHomeAsUpIndicator(upArrow);
                }
            }
        }
    }

    protected Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = findViewById(R.id.toolbar_actionbar);
        }
        return toolbar;
    }
}
