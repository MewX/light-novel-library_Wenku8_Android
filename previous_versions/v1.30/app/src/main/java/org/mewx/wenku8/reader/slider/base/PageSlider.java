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
public class PageSlider extends BaseSlider {
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

    private boolean mMoveLastPage, mMoveFirstPage;

    private int startX = 0;

    /** 滑动的view */
    private View mLeftScrollerView = null;
    private View mRightScrollerView = null;

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
        View curView = getAdapter().getUpdatedCurrentView();
        mSlidingLayout.addView(curView);
        curView.scrollTo(0, 0);

        if (getAdapter().hasPrevious()) {
            View prevView = getAdapter().getUpdatedPreviousView();
            mSlidingLayout.addView(prevView);
            prevView.scrollTo(screenWidth, 0);
        }


        if (getAdapter().hasNext()) {
            View nextView = getAdapter().getUpdatedNextView();
            mSlidingLayout.addView(nextView);
            nextView.scrollTo(-screenWidth, 0);
        }

        mSlidingLayout.slideSelected(getAdapter().getCurrent());
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
                    if (distance > 0) {
                        mDirection = MOVE_TO_LEFT;
                        mMoveLastPage = !getAdapter().hasNext();
                        mMoveFirstPage = false;

                        mSlidingLayout.slideScrollStateChanged(MOVE_TO_LEFT);

                    } else if (distance < 0) {
                        mDirection = MOVE_TO_RIGHT;
                        mMoveFirstPage = !getAdapter().hasPrevious();
                        mMoveLastPage = false;

                        mSlidingLayout.slideScrollStateChanged(MOVE_TO_RIGHT);
                    }
                }
                if (mMode == MODE_NONE
                        && ((mDirection == MOVE_TO_LEFT)
                        || (mDirection == MOVE_TO_RIGHT))) {
                    mMode = MODE_MOVE;
                }

                if (mMode == MODE_MOVE) {
                    if ((mDirection == MOVE_TO_LEFT && distance <= 0) || (mDirection == MOVE_TO_RIGHT && distance >= 0)) {
                        mMode = MODE_NONE;
                    }
                }

                if (mDirection != MOVE_NO_RESULT) {
                    if (mDirection == MOVE_TO_LEFT) {
                        mLeftScrollerView = getCurrentShowView();
                        if (!mMoveLastPage)
                            mRightScrollerView = getBottomView();
                        else mRightScrollerView = null;
                    } else {
                        mRightScrollerView = getCurrentShowView();
                        if (!mMoveFirstPage)
                            mLeftScrollerView = getTopView();
                        else mLeftScrollerView = null;
                    }
                    if (mMode == MODE_MOVE) {
                        mVelocityTracker.computeCurrentVelocity(1000, ViewConfiguration.getMaximumFlingVelocity());
                        if (mDirection == MOVE_TO_LEFT) {
                            if (mMoveLastPage) {
                                mLeftScrollerView.scrollTo(distance/2, 0);
                            } else {
                                mLeftScrollerView.scrollTo(distance, 0);
                                mRightScrollerView.scrollTo(-screenWidth + distance, 0);
                            }
                        } else {
                            if (mMoveFirstPage) {
                                mRightScrollerView.scrollTo(distance/2, 0);
                            } else {
                                mLeftScrollerView.scrollTo(screenWidth + distance, 0);
                                mRightScrollerView.scrollTo(distance, 0);
                            }
                        }
                    } else {
                        int scrollX = 0;
                        if (mLeftScrollerView != null) {
                            scrollX = mLeftScrollerView.getScrollX();
                        } else if (mRightScrollerView != null) {
                            scrollX = mRightScrollerView.getScrollX();
                        }
                        if (mDirection == MOVE_TO_LEFT && scrollX != 0 && getAdapter().hasNext()) {
                            mLeftScrollerView.scrollTo(0, 0);
                            if (mRightScrollerView != null) mRightScrollerView.scrollTo(screenWidth, 0);
                        } else if (mDirection == MOVE_TO_RIGHT && getAdapter().hasPrevious() && screenWidth != Math.abs(scrollX)) {
                            if (mLeftScrollerView != null)
                                mLeftScrollerView.scrollTo(-screenWidth, 0);
                            mRightScrollerView.scrollTo(0, 0);
                        }

                    }
                }

                invalidate();

                break;

            case MotionEvent.ACTION_UP:

                if ((mLeftScrollerView == null && mDirection == MOVE_TO_LEFT) ||
                        (mRightScrollerView == null && mDirection == MOVE_TO_RIGHT)) {
                    return false;
                }

                int time = 500;

                if (mMoveFirstPage && mRightScrollerView != null) {
                    final int rscrollx = mRightScrollerView.getScrollX();
                    mScroller.startScroll(rscrollx, 0, -rscrollx, 0, time * Math.abs(rscrollx)/screenWidth);
                    mTouchResult = MOVE_NO_RESULT;
                }

                if (mMoveLastPage && mLeftScrollerView != null) {
                    final int lscrollx = mLeftScrollerView.getScrollX();
                    mScroller.startScroll(lscrollx, 0, -lscrollx, 0, time * Math.abs(lscrollx)/screenWidth);
                    mTouchResult = MOVE_NO_RESULT;
                }

                if (!mMoveLastPage && !mMoveFirstPage && mLeftScrollerView != null) {
                    final int scrollX = mLeftScrollerView.getScrollX();
                    mVelocityValue = (int) mVelocityTracker.getXVelocity();
                    // scroll左正，右负(),(startX + dx)的值如果为0，即复位
			/*
			 * android.widget.Scroller.startScroll( int startX, int startY, int
			 * dx, int dy, int duration )
			 */

                    if (mMode == MODE_MOVE && mDirection == MOVE_TO_LEFT) {
                        if (scrollX > limitDistance || mVelocityValue < -time) {
                            // 手指向左移动，可以翻屏幕
                            mTouchResult = MOVE_TO_LEFT;
                            if (mVelocityValue < -time) {
                                int tmptime = 1000 * 1000 / Math.abs(mVelocityValue);
                                time = tmptime > 500 ? 500 : tmptime;
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
                                int tmptime = 1000 * 1000 / Math.abs(mVelocityValue);
                                time = tmptime > 500 ? 500 : tmptime;
                            }
                            mScroller.startScroll(scrollX, 0, -scrollX, 0, time);
                        } else {
                            mTouchResult = MOVE_NO_RESULT;
                            mScroller.startScroll(scrollX, 0, screenWidth - scrollX, 0, time);
                        }
                    }
                }
                resetVariables();
                invalidate();

                break;
        }

        return true;
    }

    private void invalidate() {
        mSlidingLayout.postInvalidate();
    }

    private void resetVariables() {
        mDirection = MOVE_NO_RESULT;
        mMode = MODE_NONE;
        startX = 0;
        releaseVelocityTracker();
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

    private boolean moveToNext() {
        if (!getAdapter().hasNext())
            return false;

        // Move top view to bottom view
        View prevView = getAdapter().getPreviousView();
        if (prevView != null)
            mSlidingLayout.removeView(prevView);
        View newNextView = prevView;

        getAdapter().moveToNext();

        mSlidingLayout.slideSelected(getAdapter().getCurrent());

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
            newNextView.scrollTo(-screenWidth, 0);
            mSlidingLayout.addView(newNextView);
        }

        return true;
    }

    private boolean moveToPrevious() {
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

            newPrevView.scrollTo(screenWidth, 0);
            mSlidingLayout.addView(newPrevView);
        }

        return true;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            if (mLeftScrollerView != null) {
                mLeftScrollerView.scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            }
            if (mRightScrollerView != null) {
                if (mMoveFirstPage)
                    mRightScrollerView.scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
                else
                    mRightScrollerView.scrollTo(mScroller.getCurrX() - screenWidth, mScroller.getCurrY());
            }

            invalidate();

        } else if (mScroller.isFinished()) {
            if (mTouchResult != MOVE_NO_RESULT) {
                if (mTouchResult == MOVE_TO_LEFT) {
                    moveToNext();
                } else {
                    moveToPrevious();
                }
                mTouchResult = MOVE_NO_RESULT;

                mSlidingLayout.slideScrollStateChanged(MOVE_NO_RESULT);

                invalidate();
            }

        }
    }

    @Override
    public void slideNext() {
        if (!getAdapter().hasNext() || !mScroller.isFinished())
            return;

        mLeftScrollerView = getCurrentShowView();
        mRightScrollerView = getBottomView();

        mScroller.startScroll(0, 0, screenWidth, 0, 500);
        mTouchResult = MOVE_TO_LEFT;

        mSlidingLayout.slideScrollStateChanged(MOVE_TO_LEFT);

        invalidate();
    }

    @Override
    public void slidePrevious() {
        if (!getAdapter().hasPrevious() || !mScroller.isFinished())
            return;

        mLeftScrollerView = getTopView();
        mRightScrollerView = getCurrentShowView();

        mScroller.startScroll(screenWidth, 0, -screenWidth, 0, 500);
        mTouchResult = MOVE_TO_RIGHT;

        mSlidingLayout.slideScrollStateChanged(MOVE_TO_RIGHT);

        invalidate();
    }

    public View getTopView() {
        return getAdapter().getPreviousView();
    }

    public View getCurrentShowView() {
        return getAdapter().getCurrentView();
    }

    public View getBottomView() {
        return getAdapter().getNextView();
    }
}
