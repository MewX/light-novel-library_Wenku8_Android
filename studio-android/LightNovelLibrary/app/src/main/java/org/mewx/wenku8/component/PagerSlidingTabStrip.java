/*
 * Copyright (C) 2013 Andreas Stuetz <andreas.stuetz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mewx.wenku8.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mewx.wenku8.R;

import java.util.Locale;

public class PagerSlidingTabStrip extends HorizontalScrollView {

    private static final float OPAQUE = 1.0f;
    private static final float HALF_TRANSP = 0.5f;

    public interface CustomTabProvider {
        public View getCustomTabView(ViewGroup parent, int position);
    }

    public interface OnTabReselectedListener {
        public void onTabReselected(int position);
    }

    // @formatter:off
    private static final int[] ATTRS = new int[]{
            android.R.attr.textColorPrimary,
            android.R.attr.textSize,
            android.R.attr.textColor,
            android.R.attr.paddingLeft,
            android.R.attr.paddingRight,
    };
    // @formatter:on

    private final PagerAdapterObserver adapterObserver = new PagerAdapterObserver();

    //These indexes must be related with the ATTR array above
    private static final int TEXT_COLOR_PRIMARY = 0;
    private static final int TEXT_SIZE_INDEX = 1;
    private static final int TEXT_COLOR_INDEX = 2;
    private static final int PADDING_LEFT_INDEX = 3;
    private static final int PADDING_RIGHT_INDEX = 4;

    private LinearLayout.LayoutParams defaultTabLayoutParams;
    private LinearLayout.LayoutParams expandedTabLayoutParams;

    private final PageListener pageListener = new PageListener();
    private OnTabReselectedListener tabReselectedListener = null;
    public OnPageChangeListener delegatePageListener;

    private LinearLayout tabsContainer;
    private ViewPager pager;

    private int tabCount;

    private int currentPosition = 0;
    private float currentPositionOffset = 0f;

    private Paint rectPaint;
    private Paint dividerPaint;

    private int indicatorColor;
    private int indicatorHeight = 2;

    private int underlineHeight = 0;
    private int underlineColor;

    private int dividerWidth = 0;
    private int dividerPadding = 0;
    private int dividerColor;

    private int tabPadding = 12;
    private int tabTextSize = 14;
    private ColorStateList tabTextColor = null;
    private float tabTextAlpha = HALF_TRANSP;
    private float tabTextSelectedAlpha = OPAQUE;

    private int padding = 0;

    private boolean shouldExpand = false;
    private boolean textAllCaps = true;
    private boolean isPaddingMiddle = false;

    private Typeface tabTypeface = null;
    private int tabTypefaceStyle = Typeface.BOLD;
    private int tabTypefaceSelectedStyle = Typeface.BOLD;

    private int scrollOffset;
    private int lastScrollX = 0;

    private int tabBackgroundResId = R.drawable.bg_tab;

    private Locale locale;

    public PagerSlidingTabStrip(Context context) {
        this(context, null);
    }

    public PagerSlidingTabStrip(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagerSlidingTabStrip(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFillViewport(true);
        setWillNotDraw(false);
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(tabsContainer);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        scrollOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollOffset, dm);
        indicatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, indicatorHeight, dm);
        underlineHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, underlineHeight, dm);
        dividerPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerPadding, dm);
        tabPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tabPadding, dm);
        dividerWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerWidth, dm);
        tabTextSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tabTextSize, dm);

        // get system attrs (android:textSize and android:textColor)
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);
        tabTextSize = a.getDimensionPixelSize(TEXT_SIZE_INDEX, tabTextSize);
        ColorStateList colorStateList = a.getColorStateList(TEXT_COLOR_INDEX);
        int textPrimaryColor = a.getColor(TEXT_COLOR_PRIMARY, getResources().getColor(android.R.color.white));
        if (colorStateList != null) {
            tabTextColor = colorStateList;
        } else {
            tabTextColor = getColorStateList(textPrimaryColor);
        }

        underlineColor = textPrimaryColor;
        dividerColor = textPrimaryColor;
        indicatorColor = textPrimaryColor;
        int paddingLeft = a.getDimensionPixelSize(PADDING_LEFT_INDEX, padding);
        int paddingRight = a.getDimensionPixelSize(PADDING_RIGHT_INDEX, padding);
        a.recycle();

        //In case we have the padding they must be equal so we take the biggest
        padding = Math.max(paddingLeft, paddingRight);

        // get custom attrs
        a = context.obtainStyledAttributes(attrs, R.styleable.PagerSlidingTabStrip);
        indicatorColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsIndicatorColor, indicatorColor);
        underlineColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsUnderlineColor, underlineColor);
        dividerColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsDividerColor, dividerColor);
        dividerWidth = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsDividerWidth, dividerWidth);
        indicatorHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsIndicatorHeight, indicatorHeight);
        underlineHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsUnderlineHeight, underlineHeight);
        dividerPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsDividerPadding, dividerPadding);
        tabPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsTabPaddingLeftRight, tabPadding);
        tabBackgroundResId = a.getResourceId(R.styleable.PagerSlidingTabStrip_pstsTabBackground, tabBackgroundResId);
        shouldExpand = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsShouldExpand, shouldExpand);
        scrollOffset = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsScrollOffset, scrollOffset);
        textAllCaps = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsTextAllCaps, textAllCaps);
        isPaddingMiddle = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsPaddingMiddle, isPaddingMiddle);
        tabTypefaceStyle = a.getInt(R.styleable.PagerSlidingTabStrip_pstsTextStyle, Typeface.BOLD);
        tabTypefaceSelectedStyle = a.getInt(R.styleable.PagerSlidingTabStrip_pstsTextSelectedStyle, Typeface.BOLD);
        tabTextAlpha = a.getFloat(R.styleable.PagerSlidingTabStrip_pstsTextAlpha, HALF_TRANSP);
        tabTextSelectedAlpha = a.getFloat(R.styleable.PagerSlidingTabStrip_pstsTextSelectedAlpha, OPAQUE);
        a.recycle();

        setMarginBottomTabContainer();

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);


        dividerPaint = new Paint();
        dividerPaint.setAntiAlias(true);
        dividerPaint.setStrokeWidth(dividerWidth);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        expandedTabLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

        if (locale == null) {
            locale = getResources().getConfiguration().locale;
        }
    }

    private void setMarginBottomTabContainer() {
        MarginLayoutParams mlp = (MarginLayoutParams) tabsContainer.getLayoutParams();
        int bottomMargin = indicatorHeight >= underlineHeight ? indicatorHeight : underlineHeight;
        mlp.setMargins(mlp.leftMargin, mlp.topMargin, mlp.rightMargin, bottomMargin);
        tabsContainer.setLayoutParams(mlp);
    }

    public void setViewPager(ViewPager pager) {
        this.pager = pager;
        if (pager.getAdapter() == null) {
            throw new IllegalStateException("ViewPager does not have adapter instance.");
        }

        pager.setOnPageChangeListener(pageListener);
        pager.getAdapter().registerDataSetObserver(adapterObserver);
        adapterObserver.setAttached(true);
        notifyDataSetChanged();
    }

    public void notifyDataSetChanged() {
        tabsContainer.removeAllViews();
        tabCount = pager.getAdapter().getCount();
        View tabView;
        for (int i = 0; i < tabCount; i++) {

            if (pager.getAdapter() instanceof CustomTabProvider) {
                tabView = ((CustomTabProvider) pager.getAdapter()).getCustomTabView(this, i);
            } else {
                tabView = LayoutInflater.from(getContext()).inflate(R.layout.view_tab, this, false);
            }

            CharSequence title = pager.getAdapter().getPageTitle(i);

            addTab(i, title, tabView);
        }

        updateTabStyles();
        getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @SuppressWarnings("deprecation")
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {

                getViewTreeObserver().removeOnGlobalLayoutListener(this);

                currentPosition = pager.getCurrentItem();
                currentPositionOffset = 0f;
                scrollToChild(currentPosition, 0);
                updateSelection(currentPosition);
            }
        });
    }

    private void addTab(final int position, CharSequence title, View tabView) {
        TextView textView = (TextView) tabView.findViewById(R.id.tab_title);
        if (textView != null) {
            if (title != null) textView.setText(title);
            float alpha = pager.getCurrentItem() == position ? tabTextSelectedAlpha : tabTextAlpha;
            ViewCompat.setAlpha(textView, alpha);
        }

        tabView.setFocusable(true);
        tabView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pager.getCurrentItem() != position) {
                    View tab = tabsContainer.getChildAt(pager.getCurrentItem());
                    notSelected(tab);
                    pager.setCurrentItem(position);
                } else if (tabReselectedListener != null) {
                    tabReselectedListener.onTabReselected(position);
                }
            }
        });

        tabsContainer.addView(tabView, position, shouldExpand ? expandedTabLayoutParams : defaultTabLayoutParams);
    }

    private void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            v.setBackgroundResource(tabBackgroundResId);
            v.setPadding(tabPadding, v.getPaddingTop(), tabPadding, v.getPaddingBottom());
            TextView tab_title = (TextView) v.findViewById(R.id.tab_title);

            if (tab_title != null) {
                tab_title.setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize);
                tab_title.setTypeface(tabTypeface, pager.getCurrentItem() == i ? tabTypefaceSelectedStyle : tabTypefaceStyle);
                if (tabTextColor != null) {
                    tab_title.setTextColor(tabTextColor);
                }
                // setAllCaps() is only available from API 14, so the upper case is made manually if we are on a
                // pre-ICS-build
                if (textAllCaps) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        tab_title.setAllCaps(true);
                    } else {
                        tab_title.setText(tab_title.getText().toString().toUpperCase(locale));
                    }
                }
            }
        }
    }

    private void scrollToChild(int position, int offset) {
        if (tabCount == 0) {
            return;
        }

        int newScrollX = tabsContainer.getChildAt(position).getLeft() + offset;
        if (position > 0 || offset > 0) {

            //Half screen offset.
            //- Either tabs start at the middle of the view scrolling straight away
            //- Or tabs start at the begging (no padding) scrolling when indicator gets
            //  to the middle of the view width
            newScrollX -= scrollOffset;
            Pair<Float, Float> lines = getIndicatorCoordinates();
            newScrollX += ((lines.second - lines.first) / 2);
        }

        if (newScrollX != lastScrollX) {
            lastScrollX = newScrollX;
            scrollTo(newScrollX, 0);
        }
    }

    private Pair<Float, Float> getIndicatorCoordinates() {
        // default: line below current view_tab
        View currentTab = tabsContainer.getChildAt(currentPosition);
        float lineLeft = currentTab.getLeft();
        float lineRight = currentTab.getRight();

        // if there is an offset, start interpolating left and right coordinates between current and next view_tab
        if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {

            View nextTab = tabsContainer.getChildAt(currentPosition + 1);
            final float nextTabLeft = nextTab.getLeft();
            final float nextTabRight = nextTab.getRight();

            lineLeft = (currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft);
            lineRight = (currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight);
        }
        return new Pair<>(lineLeft, lineRight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (isPaddingMiddle || padding > 0) {
            //Make sure tabContainer is bigger than the HorizontalScrollView to be able to scroll
            tabsContainer.setMinimumWidth(getWidth());
            //Clipping padding to false to see the tabs while we pass them swiping
            setClipToPadding(false);
        }

        if (tabsContainer.getChildCount() > 0) {
            tabsContainer
                    .getChildAt(0)
                    .getViewTreeObserver()
                    .addOnGlobalLayoutListener(firstTabGlobalLayoutListener);
        }
        super.onLayout(changed, l, t, r, b);
    }

    private OnGlobalLayoutListener firstTabGlobalLayoutListener = new OnGlobalLayoutListener() {

        @Override
        public void onGlobalLayout() {
            View view = tabsContainer.getChildAt(0);

            getViewTreeObserver().removeOnGlobalLayoutListener(this);

            if (isPaddingMiddle) {
                int mHalfWidthFirstTab = view.getWidth() / 2;
                padding = getWidth() / 2 - mHalfWidthFirstTab;
            }
            setPadding(padding, getPaddingTop(), padding, getPaddingBottom());
            if (scrollOffset == 0) scrollOffset = getWidth() / 2 - padding;
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInEditMode() || tabCount == 0) {
            return;
        }

        final int height = getHeight();
        // draw indicator line
        rectPaint.setColor(indicatorColor);
        Pair<Float, Float> lines = getIndicatorCoordinates();
        canvas.drawRect(lines.first + padding, height - indicatorHeight, lines.second + padding, height, rectPaint);
        // draw underline
        rectPaint.setColor(underlineColor);
        canvas.drawRect(padding, height - underlineHeight, tabsContainer.getWidth() + padding, height, rectPaint);
        // draw divider
        if (dividerWidth != 0) {
            dividerPaint.setStrokeWidth(dividerWidth);
            dividerPaint.setColor(dividerColor);
            for (int i = 0; i < tabCount - 1; i++) {
                View tab = tabsContainer.getChildAt(i);
                canvas.drawLine(tab.getRight(), dividerPadding, tab.getRight(), height - dividerPadding, dividerPaint);
            }
        }
    }

    public void setOnTabReselectedListener(OnTabReselectedListener tabReselectedListener) {
        this.tabReselectedListener = tabReselectedListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.delegatePageListener = listener;
    }

    private class PageListener implements OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            currentPosition = position;
            currentPositionOffset = positionOffset;
            int offset = tabCount > 0 ? (int) (positionOffset * tabsContainer.getChildAt(position).getWidth()) : 0;
            scrollToChild(position, offset);
            invalidate();
            if (delegatePageListener != null) {
                delegatePageListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                scrollToChild(pager.getCurrentItem(), 0);
            }
            //Full alpha for current item
            View currentTab = tabsContainer.getChildAt(pager.getCurrentItem());
            selected(currentTab);
            //Half transparent for prev item
            if (pager.getCurrentItem() - 1 >= 0) {
                View prevTab = tabsContainer.getChildAt(pager.getCurrentItem() - 1);
                notSelected(prevTab);
            }
            //Half transparent for next item
            if (pager.getCurrentItem() + 1 <= pager.getAdapter().getCount() - 1) {
                View nextTab = tabsContainer.getChildAt(pager.getCurrentItem() + 1);
                notSelected(nextTab);
            }

            if (delegatePageListener != null) {
                delegatePageListener.onPageScrollStateChanged(state);
            }
        }

        @Override
        public void onPageSelected(int position) {
            updateSelection(position);
            if (delegatePageListener != null) {
                delegatePageListener.onPageSelected(position);
            }
        }

    }

    private void updateSelection(int position) {
        for (int i = 0; i < tabCount; ++i) {
            View tv = tabsContainer.getChildAt(i);
            final boolean selected = i == position;
            tv.setSelected(selected);
            if (selected) {
                selected(tv);
            } else {
                notSelected(tv);
            }
        }
    }

    private void notSelected(View tab) {
        if (tab != null) {
            TextView title = (TextView) tab.findViewById(R.id.tab_title);
            if (title != null) {
                title.setTypeface(tabTypeface, tabTypefaceStyle);
                ViewCompat.setAlpha(title, tabTextAlpha);
            }
        }
    }

    private void selected(View tab) {
        if (tab != null) {
            TextView title = (TextView) tab.findViewById(R.id.tab_title);
            if (title != null) {
                title.setTypeface(tabTypeface, tabTypefaceSelectedStyle);
                ViewCompat.setAlpha(title, tabTextSelectedAlpha);
            }
        }
    }

    private class PagerAdapterObserver extends DataSetObserver {

        private boolean attached = false;

        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }

        public void setAttached(boolean attached) {
            this.attached = attached;
        }

        public boolean isAttached() {
            return attached;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pager != null) {
            if (!adapterObserver.isAttached()) {
                pager.getAdapter().registerDataSetObserver(adapterObserver);
                adapterObserver.setAttached(true);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pager != null) {
            if (adapterObserver.isAttached()) {
                try {
                    pager.getAdapter().unregisterDataSetObserver(adapterObserver);
                }
                catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                adapterObserver.setAttached(false);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        currentPosition = savedState.currentPosition;
        if (currentPosition != 0 && tabsContainer.getChildCount() > 0) {
            notSelected(tabsContainer.getChildAt(0));
            selected(tabsContainer.getChildAt(currentPosition));
        }
        requestLayout();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.currentPosition = currentPosition;
        return savedState;
    }

    static class SavedState extends BaseSavedState {
        int currentPosition;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPosition = in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentPosition);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public int getIndicatorColor() {
        return this.indicatorColor;
    }

    public int getIndicatorHeight() {
        return indicatorHeight;
    }

    public int getUnderlineColor() {
        return underlineColor;
    }

    public int getDividerColor() {
        return dividerColor;
    }

    public int getDividerWidth() {
        return dividerWidth;
    }

    public int getUnderlineHeight() {
        return underlineHeight;
    }

    public int getDividerPadding() {
        return dividerPadding;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public boolean getShouldExpand() {
        return shouldExpand;
    }

    public int getTextSize() {
        return tabTextSize;
    }

    public boolean isTextAllCaps() {
        return textAllCaps;
    }

    public ColorStateList getTextColor() {
        return tabTextColor;
    }

    public int getTabBackground() {
        return tabBackgroundResId;
    }

    public int getTabPaddingLeftRight() {
        return tabPadding;
    }

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        invalidate();
    }

    public void setIndicatorColorResource(int resId) {
        this.indicatorColor = getResources().getColor(resId);
        invalidate();
    }

    public void setIndicatorHeight(int indicatorLineHeightPx) {
        this.indicatorHeight = indicatorLineHeightPx;
        invalidate();
    }

    public void setUnderlineColor(int underlineColor) {
        this.underlineColor = underlineColor;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        this.underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public void setDividerColor(int dividerColor) {
        this.dividerColor = dividerColor;
        invalidate();
    }

    public void setDividerColorResource(int resId) {
        this.dividerColor = getResources().getColor(resId);
        invalidate();
    }

    public void setDividerWidth(int dividerWidthPx) {
        this.dividerWidth = dividerWidthPx;
        invalidate();
    }

    public void setUnderlineHeight(int underlineHeightPx) {
        this.underlineHeight = underlineHeightPx;
        invalidate();
    }

    public void setDividerPadding(int dividerPaddingPx) {
        this.dividerPadding = dividerPaddingPx;
        invalidate();
    }

    public void setScrollOffset(int scrollOffsetPx) {
        this.scrollOffset = scrollOffsetPx;
        invalidate();
    }

    public void setShouldExpand(boolean shouldExpand) {
        this.shouldExpand = shouldExpand;
        if (pager != null) {
            requestLayout();
        }
    }

    public void setAllCaps(boolean textAllCaps) {
        this.textAllCaps = textAllCaps;
    }

    public void setTextSize(int textSizePx) {
        this.tabTextSize = textSizePx;
        updateTabStyles();
    }

    public void setTextColor(int textColor) {
        setTextColor(getColorStateList(textColor));
    }

    private ColorStateList getColorStateList(int textColor) {
        return new ColorStateList(new int[][]{new int[]{}}, new int[]{textColor});
    }

    public void setTextColor(ColorStateList colorStateList) {
        this.tabTextColor = colorStateList;
        updateTabStyles();
    }

    public void setTextColorResource(int resId) {
        setTextColor(getResources().getColor(resId));
    }

    public void setTextColorStateListResource(int resId) {
        setTextColor(getResources().getColorStateList(resId));
    }

    public void setTypeface(Typeface typeface, int style) {
        this.tabTypeface = typeface;
        this.tabTypefaceSelectedStyle = style;
        updateTabStyles();
    }

    public void setTabBackground(int resId) {
        this.tabBackgroundResId = resId;
    }

    public void setTabPaddingLeftRight(int paddingPx) {
        this.tabPadding = paddingPx;
        updateTabStyles();
    }
}
