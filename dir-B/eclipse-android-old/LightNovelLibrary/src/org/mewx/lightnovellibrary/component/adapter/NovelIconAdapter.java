/**
 *  Novel Icon Adapter
 **
 *  Manage the list view of NovelIcon.
 **/

package org.mewx.lightnovellibrary.component.adapter;

import java.util.List;

import org.mewx.lightnovellibrary.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

public class NovelIconAdapter extends ArrayAdapter<NovelIcon> {
	private int resourceId;

	public NovelIconAdapter(Context context, int textViewResourceId,
			List<NovelIcon> objects) {
		super(context, textViewResourceId, objects);
		resourceId = textViewResourceId;
		return;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		NovelIcon ni = getItem(position);
		View view = LayoutInflater.from(getContext()).inflate(resourceId, null);

		// get view
		ImageView novelCover = (ImageView) view.findViewById(R.id.novel_cover);
		TextView novelName = (TextView) view.findViewById(R.id.novel_name);
		TextView novelHit = (TextView) view.findViewById(R.id.novel_hit_num);
		TextView novelPush = (TextView) view.findViewById(R.id.novel_push_num);
		TextView novelFav = (TextView) view.findViewById(R.id.novel_fav_num);

		// update info

		byte[] img_temp = ni.getImage();
		if (img_temp == null) {
			Log.v("MewX-XML", "img_temp == null");
		} else {
			Bitmap bitmap = BitmapFactory.decodeByteArray(img_temp, 0,
					img_temp.length);
			if (bitmap == null) {
				Log.v("MewX-XML", "Bitmap == null");
			} else {
				BitmapDrawable bmp = new BitmapDrawable(bitmap);
				if (bmp == null)
					Log.v("MewX-XML", "BitmapDrawable == null");
				else
					novelCover.setImageDrawable(bmp);
			}
		}

		novelName.setText(ni.getName());
		novelHit.setText(String.valueOf(ni.getNumHit()));
		novelPush.setText(String.valueOf(ni.getNumPush()));
		novelFav.setText(String.valueOf(ni.getNumFav()));
		return view;
	}

}
