package org.mewx.wenku8.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;

/**
 * This fragment is the parent fragment to hold all specific fragment.
 * All specific fragment is in PagerAdapter.
 */
public class RKListFragment extends Fragment {

    private MainActivity mainActivity = null;
    private MyPagerAdapter adapter;

    public RKListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }

        // get main activity
        while (mainActivity == null)
            mainActivity = (MainActivity) getActivity();

        GlobalConfig.setCurrentFragment(this); // backup
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_rklist, container, false);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Initialize the ViewPager and set an adapter
        ViewPager pager = (ViewPager) mainActivity.findViewById(R.id.rklist_pager);
        pager.setAdapter(new MyPagerAdapter(getChildFragmentManager()));

        // Bind the tabs to the ViewPager
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) mainActivity.findViewById(R.id.rklist_tabs);
        tabs.setViewPager(pager);

        // set page margin
        final int pageMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources()
                .getDisplayMetrics());
        pager.setPageMargin(pageMargin);

        // set adapter
        adapter = new MyPagerAdapter(getChildFragmentManager());
        pager.setAdapter(adapter);

        return;
    }

    @Override
    public void onDetach() {
        super.onDetach();

    }

    public class MyPagerAdapter extends FragmentPagerAdapter {

        private final Wenku8API.NOVELSORTBY[] TITLELIST = {
            Wenku8API.NOVELSORTBY.allVisit,
            Wenku8API.NOVELSORTBY.allVote,
            Wenku8API.NOVELSORTBY.monthVisit,
            Wenku8API.NOVELSORTBY.monthVote,
            Wenku8API.NOVELSORTBY.weekVisit,
            Wenku8API.NOVELSORTBY.weekVote,
            Wenku8API.NOVELSORTBY.dayVisit,
            Wenku8API.NOVELSORTBY.dayVote,
            Wenku8API.NOVELSORTBY.postDate,
            Wenku8API.NOVELSORTBY.goodNum,
            Wenku8API.NOVELSORTBY.size,
            Wenku8API.NOVELSORTBY.fullFlag,
        };

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getString(Wenku8API.getNOVELSORTBY_ChsId(TITLELIST[position]));
        }

        @Override
        public int getCount() {
            return TITLELIST.length;
        }

        @Override
        public Fragment getItem(int type) {
            Bundle bundle = new Bundle();
            bundle.putString("type", Wenku8API.getNOVELSORTBY(TITLELIST[type]));
            return NovelItemListFragment.newInstance(bundle);
        }
    }

}
