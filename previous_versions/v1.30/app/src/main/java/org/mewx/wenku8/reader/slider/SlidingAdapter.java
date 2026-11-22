package org.mewx.wenku8.reader.slider;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

/**
 * Created by xuzb on 10/22/14.
 */
public abstract class SlidingAdapter<T> {

    private View[] mViews;
    private int currentViewIndex;
    private SlidingLayout slidingLayout;

    public SlidingAdapter() {
        mViews = new View[3];
        currentViewIndex = 0;
    }

    public void setSlidingLayout(SlidingLayout slidingLayout) {
        this.slidingLayout = slidingLayout;
    }

    public View getUpdatedCurrentView() {
        View curView = mViews[currentViewIndex];
        if (curView == null) {
            curView = getView(null, getCurrent());
            mViews[currentViewIndex] = curView;
        } else {
            View updateView = getView(curView, getCurrent());
            if (curView != updateView) {
                curView = updateView;
                mViews[currentViewIndex] = updateView;
            }
        }
        return curView;
    }
    public View getCurrentView() {
        View curView = mViews[currentViewIndex];
        if (curView == null) {
            curView = getView(null, getCurrent());
            mViews[currentViewIndex] = curView;
        }
        return curView;
    }

    private View getView(int index) {
        return mViews[(index + 3) % 3];
    }

    private void setView(int index, View view) {
        mViews[(index + 3) % 3] = view;
    }

    public View getUpdatedNextView() {
        View nextView = getView(currentViewIndex + 1);
        boolean hasnext = hasNext();
        if (nextView == null && hasnext) {
            nextView = getView(null, getNext());
            setView(currentViewIndex + 1, nextView);
        } else if (hasnext) {
            View updatedView = getView(nextView, getNext());
            if (updatedView != nextView) {
                nextView = updatedView;
                setView(currentViewIndex + 1, nextView);
            }
        }
        return nextView;
    }
    public View getNextView() {
        View nextView = getView(currentViewIndex + 1);
        if (nextView == null && hasNext()) {
            nextView = getView(null, getNext());
            setView(currentViewIndex + 1, nextView);
        }
        return nextView;
    }

    public View getUpdatedPreviousView() {
        View prevView = getView(currentViewIndex - 1);
        boolean hasprev = hasPrevious();
        if (prevView == null && hasprev) {
            prevView = getView(null, getPrevious());
            setView(currentViewIndex - 1, prevView);
        } else if (hasprev) {
            View updatedView = getView(prevView, getPrevious());
            if (updatedView != prevView) {
                prevView = updatedView;
                setView(currentViewIndex - 1, prevView);
            }
        }
        return prevView;
    }

    public void setPreviousView(View view) {
        setView(currentViewIndex - 1, view);
    }

    public void setNextView(View view) {
        setView(currentViewIndex + 1, view);
    }

    public void setCurrentView(View view) {
        setView(currentViewIndex, view);
    }

    public View getPreviousView() {
        View prevView = getView(currentViewIndex - 1);
        if (prevView == null && hasPrevious()) {
            prevView = getView(null, getPrevious());
            setView(currentViewIndex - 1, prevView);
        }
        return prevView;
    }

    public void moveToNext() {
        // Move to next element
        computeNext();

        // Increase view index
        currentViewIndex = (currentViewIndex + 1) % 3;
    }

    public void moveToPrevious() {
        // Move to next element
        computePrevious();

        // Increase view index
        currentViewIndex = (currentViewIndex + 2) % 3;
    }

    public abstract View getView(View contentView, T t);

    public abstract T getCurrent();

    public abstract T getNext();

    public abstract T getPrevious();

    public abstract boolean hasNext();

    public abstract boolean hasPrevious();

    protected abstract void computeNext();

    protected abstract void computePrevious();

    public Bundle saveState() {
        return null;
    }

    public void restoreState(Parcelable parcelable, ClassLoader loader) {
        currentViewIndex = 0;
        if (mViews != null) {
            mViews[0] = null;
            mViews[1] = null;
            mViews[2] = null;
        }
    }

    public void notifyDataSetChanged() {
        if (slidingLayout != null) {
            slidingLayout.resetFromAdapter();
            slidingLayout.postInvalidate();
        }
    }
}
