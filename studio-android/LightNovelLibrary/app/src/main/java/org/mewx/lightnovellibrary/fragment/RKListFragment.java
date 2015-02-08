package org.mewx.lightnovellibrary.fragment;

import android.net.Uri;
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

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.activity.MainActivity;
import org.mewx.lightnovellibrary.adapter.NovelItemAdapter;
import org.mewx.lightnovellibrary.global.GlobalConfig;

public class RKListFragment extends Fragment {

    private MainActivity mainActivity = null;
    private MyPagerAdapter adapter;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment RKListFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static RKListFragment newInstance(String param1, String param2) {
        RKListFragment fragment = new RKListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

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
        pager.setAdapter(new MyPagerAdapter(mainActivity.getSupportFragmentManager()));

        // Bind the tabs to the ViewPager
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) mainActivity.findViewById(R.id.rklist_tabs);
        tabs.setViewPager(pager);

        final int pageMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources()
                .getDisplayMetrics());
        pager.setPageMargin(pageMargin);

        adapter = new MyPagerAdapter(mainActivity.getSupportFragmentManager());
        pager.setAdapter(adapter);

        return;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public class MyPagerAdapter extends FragmentPagerAdapter {

        private final String[] TITLES = {"Categories", "Home", "Top Paid", "Top Free", "Top Grossing", "Top New Paid",
                "Top New Free", "Trending"};

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return TITLES[position];
        }

        @Override
        public int getCount() {
            return TITLES.length;
        }

        @Override
        public Fragment getItem(int type) {
            return NovelItemListFragment.newInstance(type);
        }
    }

}
