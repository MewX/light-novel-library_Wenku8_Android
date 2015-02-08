package org.mewx.lightnovellibrary.fragment;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.mewx.lightnovellibrary.R;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FavFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link FavFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NovelItemListFragment extends Fragment {

    // type def
    private int type;
    private boolean isLoading; // judge network thread continue

    /**
     * Each position stands an specific list type.
     * @param type
     * @return
     */
    // TODO: Rename and change types and number of parameters
    public static FavFragment newInstance(int type) {
        FavFragment fragment = new FavFragment();
        Bundle args = new Bundle();
        args.putInt("type", type);
        fragment.setArguments(args);
        return fragment;
    }

    public NovelItemListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getArguments().getInt("type");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_novel_item_list,container,false);

        return rootView;
    }

}
