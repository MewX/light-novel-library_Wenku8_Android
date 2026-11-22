/**
 *  Novel Element Adapter
 **
 *  Manage the list view of NovelElement.
 **/

package org.mewx.lightnovellibrary.component.adapter;

import java.util.List;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelContentParser;
import org.mewx.lightnovellibrary.component.NovelContentParser.NovelContent;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.nostra13.universalimageloader.core.ImageLoader;

public class NovelContentAdapter extends BaseAdapter {
	private Context context;
	private List<NovelContentParser.NovelContent> ele;

	public NovelContentAdapter(Context context,
			List<NovelContentParser.NovelContent> objects) {
		this.context = context;
		this.ele = objects;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final NovelContentParser.NovelContent e = getItem(position);
		if (convertView == null) {
			// convertView = LayoutInflater.from(this.context).inflate(
			// R.layout.layout_novel_button, parent, false);
			convertView = LayoutInflater.from(this.context).inflate(
					R.layout.activity_novel_line, parent, false);
		}
		if (convertView.getTag() == null)
			convertView.setTag(String.valueOf(position));

		switch (e.type) {
		case 't':
			if (!convertView.getTag().equals(String.valueOf(position)))
				break;
			// By default, the text is visible, while the image is invisible
			((TextView) convertView.findViewById(R.id.novel_line_text))
					.setPadding(0, GlobalConfig.getShowTextPaddingTop(), 0, 0);
			((TextView) convertView.findViewById(R.id.novel_line_text))
					.setTextSize(TypedValue.COMPLEX_UNIT_SP,
							GlobalConfig.getShowTextSize());
			((TextView) convertView.findViewById(R.id.novel_line_text))
					.setText(e.content);
			break;

		case 'i':
			if (!convertView.getTag().equals(String.valueOf(position)))
				break;
			((TextView) convertView.findViewById(R.id.novel_line_text))
					.setVisibility(TextView.GONE);
			((ImageView) convertView.findViewById(R.id.novel_line_image))
					.setVisibility(ImageView.VISIBLE);
			((ImageView) convertView.findViewById(R.id.novel_line_image))
					.setPadding(0, GlobalConfig.getShowTextPaddingTop(), 0, 0);
			((ImageView) convertView.findViewById(R.id.novel_line_image))
					.setImageResource(R.drawable.empty_cover);

			// async loader
			ImageLoader.getInstance()
					.displayImage(
							e.content,
							(ImageView) convertView
									.findViewById(R.id.novel_line_image));

			break;
		}

		return convertView;
	}

	@Override
	public int getCount() {
		return this.ele.size();
	}

	@Override
	public NovelContentParser.NovelContent getItem(int position) {
		return (NovelContentParser.NovelContent) this.ele.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
}
