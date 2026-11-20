package org.mewx.wenku8.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.mewx.wenku8.component.PagerSlidingTabStrip;

import org.mewx.wenku8.R;
import org.mewx.wenku8.api.Wenku8API;

/**
 * This fragment is the parent fragment to hold all specific fragment.
 * All specific fragment is in PagerAdapter.
 */
public class RKListFragment extends Fragment {
    public RKListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rklist, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Initialize the ViewPager and set an adapter
        ViewPager pager = getActivity().findViewById(R.id.rklist_pager);
        pager.setAdapter(new MyPagerAdapter(getChildFragmentManager()));

        // Bind the tabs to the ViewPager
        PagerSlidingTabStrip tabs = getActivity().findViewById(R.id.rklist_tabs);
        tabs.setViewPager(pager);

        // set page margin
        final int pageMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources()
                .getDisplayMetrics());
        pager.setPageMargin(pageMargin);

        // set adapter
        MyPagerAdapter adapter = new MyPagerAdapter(getChildFragmentManager());
        pager.setAdapter(adapter);
    }

    public class MyPagerAdapter extends FragmentPagerAdapter {

        private final Wenku8API.NovelSortedBy[] TITLELIST = {
            Wenku8API.NovelSortedBy.allVisit,
            Wenku8API.NovelSortedBy.allVote,
            Wenku8API.NovelSortedBy.monthVisit,
            Wenku8API.NovelSortedBy.monthVote,
            Wenku8API.NovelSortedBy.weekVisit,
            Wenku8API.NovelSortedBy.weekVote,
            Wenku8API.NovelSortedBy.dayVisit,
            Wenku8API.NovelSortedBy.dayVote,
            Wenku8API.NovelSortedBy.postDate,
            Wenku8API.NovelSortedBy.goodNum,
            Wenku8API.NovelSortedBy.size,
            Wenku8API.NovelSortedBy.fullFlag,
        };

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getString(getNovelSortedByChsId(TITLELIST[position]));
        }

        @Override
        public int getCount() {
            return TITLELIST.length;
        }

        @Override
        public Fragment getItem(int type) {
            Bundle bundle = new Bundle();
            bundle.putString("type", Wenku8API.getNovelSortedBy(TITLELIST[type]));
            return NovelItemListFragment.newInstance(bundle);
        }

        public static int getNovelSortedByChsId(Wenku8API.NovelSortedBy n) {
            return switch (n) {
                case allVisit -> R.string.tab_allvisit;
                case allVote -> R.string.tab_allvote;
                case monthVisit -> R.string.tab_monthvisit;
                case monthVote -> R.string.tab_monthvote;
                case weekVisit -> R.string.tab_weekvisit;
                case weekVote -> R.string.tab_weekvote;
                case dayVisit -> R.string.tab_dayvisit;
                case dayVote -> R.string.tab_dayvote;
                case postDate -> R.string.tab_postdate;
                case lastUpdate -> R.string.tab_lastupdate;
                case goodNum -> R.string.tab_goodnum;
                case size -> R.string.tab_size;
                case fullFlag -> R.string.tab_fullflag;
            };
        }
    }

}
