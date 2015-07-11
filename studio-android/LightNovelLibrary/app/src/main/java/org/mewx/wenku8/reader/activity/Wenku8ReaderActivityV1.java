package org.mewx.wenku8.reader.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.martian.libsliding.SlidingAdapter;
import com.martian.libsliding.SlidingLayout;
import com.martian.libsliding.slider.OverlappedSlider;

import org.mewx.wenku8.R;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;
import org.mewx.wenku8.reader.setting.WenkuReaderSettingV1;
import org.mewx.wenku8.reader.view.WenkuReaderPageView;

/**
 * Created by MewX on 2015/7/10.
 */
public class Wenku8ReaderActivityV1 extends AppCompatActivity {

    // views
    private SlidingLayout mSlidingLayout;

    // components
    private WenkuReaderLoader loader;
    private WenkuReaderSettingV1 setting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_reader_swipe_temp);

        // find views
        mSlidingLayout = (SlidingLayout) findViewById(R.id.sliding_layout);

        // init components
        loader = new WenkuReaderLoaderXML();
        setting = new WenkuReaderSettingV1();

        // config sliding layout
        mSlidingLayout.setOnTapListener(new SlidingLayout.OnTapListener() {
            @Override
            public void onSingleTap(MotionEvent event) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;;
                int x = (int) event.getX();

                if (x > screenWidth / 2) {
                    mSlidingLayout.slideNext();
                } else if (x <= screenWidth / 2) {
                    mSlidingLayout.slidePrevious();
                }
            }
        });
        mSlidingLayout.setAdapter(new SlidingPageAdapter());
        mSlidingLayout.setSlider(new OverlappedSlider());
    }


    class SlidingPageAdapter extends SlidingAdapter<WenkuReaderPageView> {
        int firstLineIndex = 0; // line index of first index of this page
        int firstWordIndex = 0; // first index of this page
        int lastLineIndex = 0; // line index of last index of this page
        int lastWordIndex = 0; // last index of this page
        WenkuReaderPageView nextPage;
        WenkuReaderPageView previousPage;

        public SlidingPageAdapter() {
            super();

            // TODO: init values
        }

        @Override
        public View getView(View contentView, WenkuReaderPageView pageView) {
            if (contentView == null)
                contentView = getLayoutInflater().inflate(R.layout.layout_reader_swipe_page, null);

            WenkuReaderPageView wrpv = (WenkuReaderPageView) contentView.findViewById(R.id.sliding_layout);
            wrpv = pageView; //new WenkuReaderPageView(Wenku8ReaderActivityV1.this, loader, setting);

            return contentView;
        }

        @Override
        public boolean hasNext() {
            loader.setCurrentIndex(lastLineIndex);
            return loader.hasNext(lastWordIndex);
        }

        @Override
        protected void computeNext() {
            // vars change to next
            firstLineIndex = nextPage.getFirstLineIndex();
            firstWordIndex = nextPage.getFirstWordIndex();
            lastLineIndex = nextPage.getLastLineIndex();
            lastWordIndex = nextPage.getLastWordIndex();
        }

        @Override
        protected void computePrevious() {
            // vars change to previous
            firstLineIndex = previousPage.getFirstLineIndex();
            firstWordIndex = previousPage.getFirstWordIndex();
            lastLineIndex = previousPage.getLastLineIndex();
            lastWordIndex = previousPage.getLastWordIndex();
        }

        @Override
        public WenkuReaderPageView getNext() {
            loader.setCurrentIndex(lastLineIndex);
            nextPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, loader, setting, lastWordIndex, WenkuReaderPageView.LOADING_DIRECTION.FORWARDS);
            return nextPage;
        }

        @Override
        public boolean hasPrevious() {
            loader.setCurrentIndex(firstLineIndex);
            return loader.hasPrevious(firstWordIndex);
        }

        @Override
        public WenkuReaderPageView getPrevious() {
            loader.setCurrentIndex(firstLineIndex);
            previousPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, loader, setting, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.FORWARDS);
            return previousPage;
        }

        @Override
        public WenkuReaderPageView getCurrent() {
            loader.setCurrentIndex(firstLineIndex);
            return new WenkuReaderPageView(Wenku8ReaderActivityV1.this, loader, setting, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.CURRENT);
        }
    }
}
