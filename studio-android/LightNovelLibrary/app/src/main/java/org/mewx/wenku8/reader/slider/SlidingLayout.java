package org.mewx.wenku8.reader.slider;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.mewx.wenku8.reader.slider.base.Slider;

/**
 * Created by xuzb on 10/23/14.
 */
public class SlidingLayout extends ViewGroup {

    // 用于记录点击事件
    private int mDownMotionX, mDownMotionY;
    private long mDownMotionTime;

    private OnTapListener mOnTapListener;

    private Slider mSlider;

    SlidingAdapter mAdapter;

    private Parcelable mRestoredAdapterState = null;
    private ClassLoader mRestoredClassLoader = null;

    public SlidingLayout(Context context) {
        super(context);
    }

    public SlidingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SlidingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSlider(Slider slider) {
        mSlider = slider;
        slider.init(this);
        resetFromAdapter();
    }

    public SlidingAdapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(SlidingAdapter adapter) {
        mAdapter = adapter;

        mAdapter.setSlidingLayout(this);
        if (mRestoredAdapterState != null) {
            mAdapter.restoreState(mRestoredAdapterState, mRestoredClassLoader);
            mRestoredAdapterState = null;
            mRestoredClassLoader = null;
        }

        resetFromAdapter();

        postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownMotionX = (int) event.getX();
                mDownMotionY = (int) event.getY();
                mDownMotionTime = System.currentTimeMillis();
                break;

            case MotionEvent.ACTION_UP:
                computeTapMotion(event);
                break;
        }

        return mSlider.onTouchEvent(event) || super.onTouchEvent(event);

    }

    public void setOnTapListener(OnTapListener l) {
        this.mOnTapListener = l;
    }

    private void computeTapMotion(MotionEvent event) {
        if (mOnTapListener == null)
            return;

        int xDiff = Math.abs((int) event.getX() - mDownMotionX);
        int yDiff = Math.abs((int) event.getY() - mDownMotionY);
        long timeDiff = System.currentTimeMillis() - mDownMotionTime;

        if (xDiff < 5 && yDiff < 5 && timeDiff < 200) {
            mOnTapListener.onSingleTap(event);
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        mSlider.computeScroll();
    }

    public void slideNext() {
        mSlider.slideNext();
    }

    public void slidePrevious() {
        mSlider.slidePrevious();
    }

    public interface OnTapListener {
        public void onSingleTap(MotionEvent event);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int height = child.getMeasuredHeight();
            int width = child.getMeasuredWidth();
            child.layout(0, 0, width, height);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public static class SavedState extends BaseSavedState {
        Parcelable adapterState;
        ClassLoader loader;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeParcelable(adapterState, flags);
        }

        @Override
        public String toString() {
            return "BaseSlidingLayout.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + "}";
        }

        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        });

        SavedState(Parcel in, ClassLoader loader) {
            super(in);
            if (loader == null) {
                loader = getClass().getClassLoader();
            }
            adapterState = in.readParcelable(loader);
            this.loader = loader;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        if (mAdapter != null) {
            ss.adapterState = mAdapter.saveState();
        }
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (mAdapter != null) {
            mAdapter.restoreState(ss.adapterState, ss.loader);
            resetFromAdapter();
        } else {
            mRestoredAdapterState = ss.adapterState;
            mRestoredClassLoader = ss.loader;
        }
    }

    public void resetFromAdapter() {
        removeAllViews();
        if (mSlider != null && mAdapter != null)
            mSlider.resetFromAdapter(mAdapter);
    }

    private OnSlideChangeListener mSlideChangeListener;
    public void setOnSlideChangeListener(OnSlideChangeListener l) {
        mSlideChangeListener = l;
    }

    public interface OnSlideChangeListener {
        public void onSlideScrollStateChanged(int touchResult);
        public void onSlideSelected(Object obj);
    }

    public void slideScrollStateChanged(int moveDirection) {
        if (mSlideChangeListener != null)
            mSlideChangeListener.onSlideScrollStateChanged(moveDirection);
    }

    public void slideSelected(Object obj) {
        if (mSlideChangeListener != null)
            mSlideChangeListener.onSlideSelected(obj);
    }
}
