package org.mewx.wenku8.reader.slider.base;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import org.mewx.wenku8.reader.slider.SlidingAdapter;
import org.mewx.wenku8.reader.slider.SlidingLayout;

/**
 * Created by xuzb on 1/16/15.
 */
public class OverlappedSlider extends BaseSlider {

    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private int mVelocityValue = 0;

    /** 商定这个滑动是否有效的距离 */
    private int limitDistance = 0;

    private int screenWidth = 0;

    /** 最后触摸的结果方向 */
    private int mTouchResult = MOVE_NO_RESULT;
    /** 一开始的方向 */
    private int mDirection = MOVE_NO_RESULT;

    private int mMode = MODE_NONE;

    /** 滑动的view */
    private View mScrollerView = null;

    private int startX = 0;

    private SlidingLayout mSlidingLayout;

    private SlidingAdapter getAdapter() {
        return mSlidingLayout.getAdapter();
    }

    @Override
    public void init(SlidingLayout slidingLayout) {
        mSlidingLayout = slidingLayout;
        mScroller = new Scroller(slidingLayout.getContext());
        screenWidth = slidingLayout.getContext().getResources().getDisplayMetrics().widthPixels;
        limitDistance = screenWidth / 3;
    }

    @Override
    public void resetFromAdapter(SlidingAdapter adapter) {
        mSlidingLayout.addView(getAdapter().getCurrentView());

        if (getAdapter().hasNext()) {
            View nextView = getAdapter().getNextView();
            mSlidingLayout.addView(nextView, 0);
            nextView.scrollTo(0, 0);
        }

        if (getAdapter().hasPrevious()) {
            View prevView = getAdapter().getPreviousView();
            mSlidingLayout.addView(prevView);
            prevView.scrollTo(screenWidth, 0);
        }

        mSlidingLayout.slideSelected(getAdapter().getCurrent());
    }

    public View getTopView() {
        return getAdapter().getPreviousView();
    }

    public View getCurrentShowView() {
        return getAdapter().getCurrentView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        obtainVelocityTracker(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mScroller.isFinished()) {
                    break;
                }
                startX = (int) event.getX();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mScroller.isFinished()) {
                    return false;
                }
                if (startX == 0) {
                    startX = (int) event.getX();
                }
                final int distance = startX - (int) event.getX();
                if (mDirection == MOVE_NO_RESULT) {
                    if (getAdapter().hasNext() && distance > 0) {
                        mDirection = MOVE_TO_LEFT;
                    } else if (getAdapter().hasPrevious() && distance < 0) {
                        mDirection = MOVE_TO_RIGHT;
                    }
                }
                if (mMode == MODE_NONE
                        && ((mDirection == MOVE_TO_LEFT && getAdapter().hasNext())
                        || (mDirection == MOVE_TO_RIGHT && getAdapter().hasPrevious()))) {
                    mMode = MODE_MOVE;
                }

                if (mMode == MODE_MOVE) {
                    if ((mDirection == MOVE_TO_LEFT && distance <= 0) || (mDirection == MOVE_TO_RIGHT && distance >= 0)) {
                        mMode = MODE_NONE;
                    }
                }

                if (mDirection != MOVE_NO_RESULT) {
                    if (mDirection == MOVE_TO_LEFT) {
                        mScrollerView = getCurrentShowView();
                    } else {
                        mScrollerView = getTopView();
                    }
                    if (mMode == MODE_MOVE) {
                        mVelocityTracker.computeCurrentVelocity(1000, ViewConfiguration.getMaximumFlingVelocity());
                        if (mDirection == MOVE_TO_LEFT) {
                            mScrollerView.scrollTo(distance, 0);
                        } else {
                            mScrollerView.scrollTo(screenWidth + distance, 0);
                        }
                    } else {
                        final int scrollX = mScrollerView.getScrollX();
                        if (mDirection == MOVE_TO_LEFT && scrollX != 0 && getAdapter().hasNext()) {
                            mScrollerView.scrollTo(0, 0);
                        } else if (mDirection == MOVE_TO_RIGHT && getAdapter().hasPrevious() && screenWidth != Math.abs(scrollX)) {
                            mScrollerView.scrollTo(screenWidth, 0);
                        }

                    }
                }

                invalidate();

                break;

            case MotionEvent.ACTION_UP:
                if (mScrollerView == null) {
                    return false;
                }
                final int scrollX = mScrollerView.getScrollX();
                mVelocityValue = (int) mVelocityTracker.getXVelocity();
                // scroll左正，右负(),(startX + dx)的值如果为0，即复位
			/*
			 * android.widget.Scroller.startScroll( int startX, int startY, int
			 * dx, int dy, int duration )
			 */

                int time = 500;

                if (mMode == MODE_MOVE && mDirection == MOVE_TO_LEFT) {
                    if (scrollX > limitDistance || mVelocityValue < -time) {
                        // 手指向左移动，可以翻屏幕
                        mTouchResult = MOVE_TO_LEFT;
                        if (mVelocityValue < -time) {
                            time = 200;
                        }
                        mScroller.startScroll(scrollX, 0, screenWidth - scrollX, 0, time);
                    } else {
                        mTouchResult = MOVE_NO_RESULT;
                        mScroller.startScroll(scrollX, 0, -scrollX, 0, time);
                    }
                } else if (mMode == MODE_MOVE && mDirection == MOVE_TO_RIGHT) {
                    if ((screenWidth - scrollX) > limitDistance || mVelocityValue > time) {
                        // 手指向右移动，可以翻屏幕
                        mTouchResult = MOVE_TO_RIGHT;
                        if (mVelocityValue > time) {
                            time = 250;
                        }
                        mScroller.startScroll(scrollX, 0, -scrollX, 0, time);
                    } else {
                        mTouchResult = MOVE_NO_RESULT;
                        mScroller.startScroll(scrollX, 0, screenWidth - scrollX, 0, time);
                    }
                }
                resetVariables();
                invalidate();
                break;
        }
        return true;
    }
    private void resetVariables() {
        mDirection = MOVE_NO_RESULT;
        mMode = MODE_NONE;
        startX = 0;
        releaseVelocityTracker();
    }

    public boolean moveToNext() {
        if (!getAdapter().hasNext())
            return false;

        // Move top view to bottom view
        View prevView = getAdapter().getPreviousView();
        if (prevView != null)
            mSlidingLayout.removeView(prevView);
        View newNextView = prevView;

        getAdapter().moveToNext();

        if (getAdapter().hasNext()) {
            // Update content in the old view
            if (newNextView != null) {
                View updateNextView = getAdapter().getView(newNextView, getAdapter().getNext());
                if (updateNextView != newNextView) {
                    getAdapter().setNextView(updateNextView);
                    newNextView = updateNextView;
                }
            } else {
                newNextView = getAdapter().getNextView();
            }
            mSlidingLayout.addView(newNextView, 0);
            newNextView.scrollTo(0, 0);
        }

        return true;
    }

    public boolean moveToPrevious() {
        if (!getAdapter().hasPrevious())
            return false;

        // Move top view to bottom view
        View nextView = getAdapter().getNextView();
        if (nextView != null)
            mSlidingLayout.removeView(nextView);
        View newPrevView = nextView;

        getAdapter().moveToPrevious();

        mSlidingLayout.slideSelected(getAdapter().getCurrent());

        if (getAdapter().hasPrevious()) {
            // Reuse the previous view as the next view
            // Update content in the old view
            if (newPrevView != null) {
                View updatedPrevView = getAdapter().getView(newPrevView, getAdapter().getPrevious());
                if (newPrevView != updatedPrevView) {
                    getAdapter().setPreviousView(updatedPrevView);
                    newPrevView = updatedPrevView;
                }
            } else {
                newPrevView = getAdapter().getPreviousView();
            }
            mSlidingLayout.addView(newPrevView);
            newPrevView.scrollTo(screenWidth, 0);
        }

        return true;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mScrollerView.scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
        } else if (mScroller.isFinished() && mTouchResult != MOVE_NO_RESULT) {
            if (mTouchResult == MOVE_TO_LEFT) {
                moveToNext();
            } else {
                moveToPrevious();
            }
            mTouchResult = MOVE_NO_RESULT;
            invalidate();
        }
    }

    private void invalidate() {
        mSlidingLayout.postInvalidate();
    }

    private void obtainVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void slideNext() {
        if (!getAdapter().hasNext() || !mScroller.isFinished())
            return;

        mScrollerView = getCurrentShowView();

        mScroller.startScroll(0, 0, screenWidth, 0, 500);
        mTouchResult = MOVE_TO_LEFT;

        mSlidingLayout.slideScrollStateChanged(MOVE_TO_LEFT);

        invalidate();
    }

    @Override
    public void slidePrevious() {
        if (!getAdapter().hasPrevious() || !mScroller.isFinished())
            return;

        mScrollerView = getTopView();

        mScroller.startScroll(screenWidth, 0, -screenWidth, 0, 500);
        mTouchResult = MOVE_TO_RIGHT;

        mSlidingLayout.slideScrollStateChanged(MOVE_TO_RIGHT);

        invalidate();
    }
}
