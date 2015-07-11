package org.mewx.wenku8.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;
import com.umeng.update.UmengUpdateListener;
import com.umeng.update.UpdateResponse;
import com.umeng.update.UpdateStatus;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;

public class ConfigFragment extends Fragment {

    // views
    private TextView tvNotice;

    public static ConfigFragment newInstance() {
        return new ConfigFragment();
    }

    public ConfigFragment() {
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
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // get views
        tvNotice = (TextView) getActivity().findViewById(R.id.notice);
        if(Wenku8API.NoticeString.equals(""))
            getActivity().findViewById(R.id.notice_layout).setVisibility(View.GONE);
        else
            tvNotice.setText("通知：\n" + Wenku8API.NoticeString);

        // set all on click listeners
        getActivity().findViewById(R.id.btn_check_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!GlobalConfig.inAlphaBuild()) {
                    // alpha version does not contains auto-update function
                    // check for update
                    UmengUpdateAgent.setUpdateListener(new UmengUpdateListener() {
                        @Override
                        public void onUpdateReturned(int updateStatus, UpdateResponse updateInfo) {
                            switch (updateStatus) {
                                case UpdateStatus.Yes: // has update
                                    break;
                                case UpdateStatus.No: // has no update
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_latest_version), Toast.LENGTH_SHORT).show();
                                    break;
                                case UpdateStatus.NoneWifi: // none wifi
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_nonewifi), Toast.LENGTH_SHORT).show();
                                    break;
                                case UpdateStatus.Timeout: // time out
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_timeout), Toast.LENGTH_SHORT).show();
                                    break;
                            }
                        }
                    });
                    UmengUpdateAgent.forceUpdate(getActivity());
                }
                else {
                    //Toast.makeText(getActivity(), "值得骄傲的内测用户：\n请从群共享里面下载最新版本~", Toast.LENGTH_SHORT).show();

                    UmengUpdateAgent.setUpdateAutoPopup(false);
                    UmengUpdateAgent.setUpdateListener(new UmengUpdateListener() {
                        @Override
                        public void onUpdateReturned(int updateStatus, UpdateResponse updateInfo) {
                            switch (updateStatus) {
                                case UpdateStatus.Yes: // has update
//                                    if (UmengUpdateAgent.isIgnore(getActivity(), updateInfo)) {
//                                        Toast.makeText(getActivity(), getResources().getString(R.string.system_update_ignored), Toast.LENGTH_SHORT).show();
//                                    } else {
                                        new MaterialDialog.Builder(getActivity())
                                                .forceStacking(true)
                                                .theme(Theme.LIGHT)
                                                .titleColor(R.color.default_text_color_black)
                                                .backgroundColorRes(R.color.dlgBackgroundColor)
                                                .contentColorRes(R.color.dlgContentColor)
                                                .positiveColorRes(R.color.dlgPositiveButtonColor)
                                                .title("New: " + updateInfo.version)
                                                .content(updateInfo.updateLog)
                                                .titleGravity(GravityEnum.CENTER)
                                                .positiveText(R.string.dialog_positive_gotit)
                                                .show();
//                                    }
                                    break;
                                case UpdateStatus.No: // has no update
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_latest_version), Toast.LENGTH_SHORT).show();
                                    break;
                                case UpdateStatus.NoneWifi: // none wifi
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_nonewifi), Toast.LENGTH_SHORT).show();
                                    break;
                                case UpdateStatus.Timeout: // time out
                                    Toast.makeText(getActivity(), getResources().getString(R.string.system_update_timeout), Toast.LENGTH_SHORT).show();
                                    break;
                            }
                        }
                    });
                    UmengUpdateAgent.update(getActivity());
                }
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("ConfigFragment");
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("ConfigFragment");
    }
}
