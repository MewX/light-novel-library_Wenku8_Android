package org.mewx.wenku8.fragment;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IntegerRes;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;

import java.util.ArrayList;
import java.util.List;


public class FavFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    // local vars
    private LinearLayoutManager mLayoutManager = null;
    private RecyclerView mRecyclerView = null;

    // novel list info
    private List<Integer> listNovelItemAid = null; // aid list
    private List<NovelItemInfoUpdate> listNovelItemInfo = null; // novel info list
    private NovelItemAdapterUpdate mAdapter = null;

    public static FavFragment newInstance(String param1, String param2) {
        FavFragment fragment = new FavFragment();
        return fragment;
    }

    public FavFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_fav, container, false);

        // find view
        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return null;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            }

            @Override
            public int getItemCount() {
                return 0;
            }
        }); // an empty one?

        // load all info to recycler view
        listNovelItemAid = GlobalConfig.getLocalBookshelfList();
        listNovelItemInfo = new ArrayList<NovelItemInfoUpdate>();
        AsyncLoadAllLocal alal = new AsyncLoadAllLocal();
        alal.execute();

        return rootView;
    }

    @Override
    public void onItemClick(View view, int position) {
        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "fav");
        GlobalConfig.accessToLocalBookshelf(listNovelItemAid.get(position)); // sort event
        startActivity(intent);

    }

    @Override
    public void onItemLongClick(View view, int position) {
        Toast.makeText(getActivity(),"item long click detected", Toast.LENGTH_SHORT).show();

    }

    private void refreshList() {
//        listNovelItemAid = GlobalConfig.getLocalBookshelfList(); // reget
//        List<NovelItemInfoUpdate> resort = new ArrayList<NovelItemInfoUpdate>();
//        for(Integer aid : listNovelItemAid) {
//            for(int i = 0; i < listNovelItemInfo.size(); i ++) {
//                if(listNovelItemInfo.get(i).aid == aid) {
//                    resort.add(listNovelItemInfo.get(i));
//                    listNovelItemInfo.remove(i);
//                    break;
//                }
//            }
//        }
//        listNovelItemInfo = resort;

        // awful way
        listNovelItemAid = GlobalConfig.getLocalBookshelfList();
        listNovelItemInfo = new ArrayList<NovelItemInfoUpdate>();
        AsyncLoadAllLocal alal = new AsyncLoadAllLocal();
        alal.execute();
    }

    private class AsyncLoadAllLocal extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {
            // load all meta file
            for(Integer aid : listNovelItemAid) {
                String xml = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
                NovelItemInfoUpdate niiu = null;

                if (xml.equals("")) {
                    // the intro file was deleted
                    Toast.makeText(getActivity(), aid + getResources().getString( R.string.bookshelf_intro_load_failed),
                            Toast.LENGTH_SHORT).show();
                    niiu = new NovelItemInfoUpdate(aid);
                }
                else {
                    //niiu = NovelItemInfoUpdate.convertFromMeta(Wenku8Parser.parsetNovelFullMeta(xml));
                    niiu = NovelItemInfoUpdate.parse(xml);
                }

                if(niiu == null)
                    return -1;
                listNovelItemInfo.add(niiu);
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            if(integer == -1) {
                Toast.makeText(getActivity(), "Error: niiu == null", Toast.LENGTH_SHORT).show();
                return;
            }

            mAdapter = new NovelItemAdapterUpdate();
            mAdapter.RefreshDataset(listNovelItemInfo);
            mAdapter.setOnItemClickListener(FavFragment.this);
            mAdapter.setOnItemLongClickListener(FavFragment.this);
            mRecyclerView.setAdapter(mAdapter);
        }
    }

    private class AsyncLoadAllCloud extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            // load and merge with local

            // check local only books

            // check cloud capacity, if enough, sync to cloud, toast; or show local only ribbon
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("FavFragment");
        GlobalConfig.LeaveBookshelf();
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("FavFragment");
        GlobalConfig.EnterBookshelf();

        // refresh list
        refreshList();
//        if(mAdapter != null) {
//            mAdapter.notifyDataSetChanged();
//            mRecyclerView.setAdapter(mAdapter);
//        }
    }

}
