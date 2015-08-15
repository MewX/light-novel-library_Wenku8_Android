/**
 *  Novel Element Adapter
 **
 *  Manage the list view of NovelElement.
 **/

package org.mewx.lightnovellibrary.component.adapter;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import com.nostra13.universalimageloader.core.ImageLoader;

public class NovelElementAdapter extends BaseAdapter {
	private Context context;
	private Adapter ad;
	private List<NovelElement> ele;
	private List<Integer> imgList;

	public NovelElementAdapter(Context context, List<NovelElement> objects) {
		this.context = context;
		this.ele = objects;
		this.imgList = new ArrayList<Integer>(); // init queue
		this.ad = this;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final NovelElement e = getItem(position);
		if (convertView == null) {
			// convertView = LayoutInflater.from(this.context).inflate(
			// R.layout.layout_novel_button, parent, false);
			convertView = LayoutInflater.from(this.context).inflate(
					R.layout.activity_novel_button, parent, false);
		}

		// set texts
		TextView txt = (TextView) convertView.findViewById(R.id.novel_name);
		txt.setText(e.getName());
		txt = (TextView) convertView.findViewById(R.id.novel_hit_num);
		txt.setText(String.valueOf(e.getNumHit()));
		txt = (TextView) convertView.findViewById(R.id.novel_push_num);
		txt.setText(String.valueOf(e.getNumPush()));
		txt = (TextView) convertView.findViewById(R.id.novel_fav_num);
		txt.setText(String.valueOf(e.getNumFav()));

		// set image
		final ImageView iv = (ImageView) convertView
				.findViewById(R.id.novel_cover);
		Log.v("MewX-View", "ImageView=" + iv.toString());

		if (e.getImgUrl() == null)
			iv.setImageResource(R.drawable.empty_cover);
		else
			ImageLoader.getInstance().displayImage(e.getImgUrl(), iv);

		// Asysnc load image
		class imgTask extends AsyncTask<List<NameValuePair>, Integer, byte[]> {
			NovelElement e_bak = e;
			ImageView iv_bak = iv;

			@Override
			protected byte[] doInBackground(List<NameValuePair>... params) {
				// TODO Auto-generated method stub
				return LightNetwork.LightHttpPost(Wenku8Interface.BaseURL,
						params[0]);
			}

			@Override
			protected void onPostExecute(byte[] result) {
				// result:
				// add imageView, only here can fetch the layout2 id!!!
				// layout2 = (LinearLayout) parentActivity
				// .findViewById(R.id.layout_tab1);
				// if (layout2 == null) {
				// Log.v("MewX-Main", "LinearLayout == null");
				// return;
				// }
				if (result == null)
					return;
				Bitmap bitmap = BitmapFactory.decodeByteArray(result, 0,
						result.length);
				if (bitmap == null) {
					Log.v("MewX-Main", "Bitmap == null");
					return;
				}

				// save file
				if (LightCache.saveFile(GlobalConfig.getFirstStoragePath()
						+ "imgs" + File.separator,
						String.valueOf(e_bak.getAid()) + ".jpg", result, true) == false) {
					LightCache.saveFile(GlobalConfig.getSecondStoragePath()
							+ "imgs" + File.separator,
							String.valueOf(e_bak.getAid()) + ".jpg", result,
							true);
					e_bak.setImgUrl("file://"
							+ GlobalConfig.getSecondStoragePath() + "imgs"
							+ File.separator + String.valueOf(e_bak.getAid())
							+ ".jpg");
				} else
					e_bak.setImgUrl("file://"
							+ GlobalConfig.getFirstStoragePath() + "imgs"
							+ File.separator + String.valueOf(e_bak.getAid())
							+ ".jpg");
				// BitmapDrawable bmp1 = new BitmapDrawable(bitmap);
				// if (bmp1 == null) {
				// Log.v("MewX-Main", "BitmapDrawable == null");
				// return;
				// }
				// iv_bak.setImageDrawable(bmp1);

				ImageLoader.getInstance().displayImage((e_bak.getImgUrl()),
						iv_bak);
				imgList.remove(new Integer(e_bak.getAid())); // remove
				// ((BaseAdapter) ad).notifyDataSetChanged();

				// layout2.addView(image1);
				return;

			}

		}

		if (e.getImgUrl() == null) {
			if (LightCache.testFileExist(GlobalConfig.getFirstStoragePath()
					+ "imgs" + File.separator + String.valueOf(e.getAid())
					+ ".jpg") == true) {
				e.setImgUrl("file://" + GlobalConfig.getFirstStoragePath()
						+ "imgs" + File.separator + String.valueOf(e.getAid())
						+ ".jpg");

				// load image
				ImageLoader.getInstance().displayImage((e.getImgUrl()), iv);
			} else if (LightCache.testFileExist(GlobalConfig
					.getSecondStoragePath()
					+ "imgs"
					+ File.separator
					+ String.valueOf(e.getAid()) + ".jpg") == true) {
				e.setImgUrl("file://" + GlobalConfig.getSecondStoragePath()
						+ "imgs" + File.separator + String.valueOf(e.getAid())
						+ ".jpg");

				// load image
				ImageLoader.getInstance().displayImage((e.getImgUrl()), iv);

			} else if (addImgToList(e.getAid())) {
				List<NameValuePair> targImg = new ArrayList<NameValuePair>();
				targImg.add(Wenku8Interface.getNovelCover(e.getAid()));

				imgTask ast = new imgTask();
				ast.execute(targImg);
			}
		}

		return convertView;
	}

	@Override
	public int getCount() {
		return this.ele.size();
	}

	@Override
	public NovelElement getItem(int position) {
		return (NovelElement) this.ele.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	private boolean addImgToList(int aid) {
		if (!imgList.isEmpty())
			for (int i = 0; i < imgList.size(); i++)
				if (imgList.get(i).equals(new Integer(aid))) {
					Log.v("MewX", "----- Found \"" + aid + "\" in LIST! -----");
					return false;

				}

		imgList.add(new Integer(aid));
		return true;
	}
}
