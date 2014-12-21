/**
 *  Entry Element Adapter
 **
 *  Manage the list view of EntryElement.
 **/

package org.mewx.lightnovellibrary.component.adapter;

import java.util.List;

import org.mewx.lightnovellibrary.R;

import com.nostra13.universalimageloader.core.ImageLoader;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class EntryElementAdapter extends BaseAdapter {
	private Context context;
	private List<EntryElement> ele;

	public EntryElementAdapter(Context context, List<EntryElement> objects) {
		this.context = context;
		this.ele = objects;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		EntryElement e = getItem(position);
		if (convertView == null) {
			convertView = LayoutInflater.from(this.context).inflate(
					R.layout.layout_entry_button, parent, false);
		}
		
		TextView txt = (TextView) convertView.findViewById(R.id.entry_name);
		txt.setText(e.getName());
		ImageLoader.getInstance().displayImage(e.getUrl(),
				(ImageView) convertView.findViewById(R.id.entry_icon));
		return convertView;
	}

	@Override
	public int getCount() {
		return this.ele.size();
	}

	@Override
	public EntryElement getItem(int position) {
		return (EntryElement) this.ele.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
}
