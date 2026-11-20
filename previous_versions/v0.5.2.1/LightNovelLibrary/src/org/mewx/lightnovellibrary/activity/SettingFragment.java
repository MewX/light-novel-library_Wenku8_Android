/**
 *  Bookshelf Fragment
 **
 *  This class is a part of main activity, and it will show you bookshelf.
 *  Bookshelf contains the books you've clicked "like".
 *  And if the book is cached, you can read it offline.
 **/

package org.mewx.lightnovellibrary.activity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mewx.lightnovellibrary.R;

import com.ecloud.pulltozoomview.PullToZoomScrollView;
import com.ecloud.pulltozoomview.PullToZoomScrollViewEx;
import com.special.ResideMenu.ResideMenu;

import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.util.Log;
import android.util.Property;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;
import com.zcw.togglebutton.ToggleButton;

@TargetApi(14)
public class SettingFragment extends Fragment {
	private ResideMenu resideMenu;
	private MainActivity parentActivity = null;
	LinearLayout ll = null; // main LinearLayout
	private TextView textView_MewX;

	private ResideMenu.OnMenuListener menuListener = new ResideMenu.OnMenuListener() {
		@Override
		public void openMenu() {
			//
		}

		@Override
		public void closeMenu() {
			//
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View parentView = inflater.inflate(R.layout.activity_setting,
				container, false);
		setUpViews();

		// set the two button on the title bar
		((ImageView) parentActivity.findViewById(R.id.btnMenu))
				.setVisibility(View.VISIBLE);
		((ImageView) parentActivity.findViewById(R.id.btnEdit))
				.setVisibility(View.GONE);

		return parentView;
	}

	private void setUpViews() {
		while (parentActivity == null) {
			// this step is necessary
			parentActivity = (MainActivity) getActivity();
		}
		resideMenu = parentActivity.getResideMenu();

		resideMenu.setMenuListener(menuListener);
		resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

		return;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		loadViewForCode();

		// get parent layout
		ll = (LinearLayout) parentActivity.findViewById(R.id.setting_content);

		// add network setting
		RelativeLayout rl = (RelativeLayout) LayoutInflater
				.from(parentActivity)
				.inflate(R.layout.activity_setting_button_switcher, null)
				.findViewById(R.id.setting_item_container);
		if (rl == null)
			Log.e("MewX", "rl == null");
		((TextView) rl.findViewById(R.id.main_text)).setText(getResources()
				.getString(R.string.setting_network));
		((TextView) rl.findViewById(R.id.sub_text)).setText(getResources()
				.getString(R.string.setting_click_to_view_more));
		((ToggleButton) rl.findViewById(R.id.switcher))
				.setVisibility(View.GONE);
		rl.setClickable(true);
		rl.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Toast.makeText(parentActivity,
						getResources().getString(R.string.time_tight),
						Toast.LENGTH_SHORT).show();
			}
		});
		ll.addView(rl);

		// make seperate line
		ImageView seperateIV1 = new ImageView(parentActivity);
		seperateIV1.setBackgroundColor(getResources().getColor(
				R.color.setting_red));
		seperateIV1.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
						getResources().getDisplayMetrics())));
		ll.addView(seperateIV1); // add seperate line

		// add general setting
		rl = (RelativeLayout) LayoutInflater.from(parentActivity)
				.inflate(R.layout.activity_setting_button_switcher, null)
				.findViewById(R.id.setting_item_container);
		if (rl == null)
			Log.e("MewX", "rl == null");
		((TextView) rl.findViewById(R.id.main_text)).setText(getResources()
				.getString(R.string.setting_general));
		((TextView) rl.findViewById(R.id.sub_text)).setText(getResources()
				.getString(R.string.setting_click_to_view_more));
		((ToggleButton) rl.findViewById(R.id.switcher))
				.setVisibility(View.GONE);
		rl.setClickable(true);
		rl.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Toast.makeText(parentActivity,
						getResources().getString(R.string.time_tight),
						Toast.LENGTH_SHORT).show();
			}
		});
		ll.addView(rl);

		// make seperate line
		ImageView seperateIV2 = new ImageView(parentActivity);
		seperateIV2.setBackgroundColor(getResources().getColor(
				R.color.setting_red));
		seperateIV2.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
						getResources().getDisplayMetrics())));
		ll.addView(seperateIV2); // add seperate line

		// add reader setting
		rl = (RelativeLayout) LayoutInflater.from(parentActivity)
				.inflate(R.layout.activity_setting_button_switcher, null)
				.findViewById(R.id.setting_item_container);
		if (rl == null)
			Log.e("MewX", "rl == null");
		((TextView) rl.findViewById(R.id.main_text)).setText(getResources()
				.getString(R.string.setting_reader));
		((TextView) rl.findViewById(R.id.sub_text)).setText(getResources()
				.getString(R.string.setting_click_to_view_more));
		((ToggleButton) rl.findViewById(R.id.switcher))
				.setVisibility(View.GONE);
		rl.setClickable(true);
		rl.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Toast.makeText(parentActivity,
						getResources().getString(R.string.time_tight),
						Toast.LENGTH_SHORT).show();
			}
		});
		ll.addView(rl);

		// make seperate line
		ImageView seperateIV3 = new ImageView(parentActivity);
		seperateIV3.setBackgroundColor(getResources().getColor(
				R.color.setting_red));
		seperateIV3.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
						getResources().getDisplayMetrics())));
		ll.addView(seperateIV3); // add seperate line

		// add about
		rl = (RelativeLayout) LayoutInflater.from(parentActivity)
				.inflate(R.layout.activity_setting_button_switcher, null)
				.findViewById(R.id.setting_item_container);
		if (rl == null)
			Log.e("MewX", "rl == null");
		((TextView) rl.findViewById(R.id.main_text)).setText(getResources()
				.getString(R.string.setting_about));
		((TextView) rl.findViewById(R.id.sub_text)).setText(getResources()
				.getString(R.string.setting_click_to_view_more));
		// ((ToggleButton) rl.findViewById(R.id.switcher))
		// .setVisibility(View.GONE);
		rl.setClickable(true);
		rl.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(parentActivity, AboutActivity.class);
				startActivity(intent);
				parentActivity.overridePendingTransition(R.anim.in_from_right,
						R.anim.keep);
			}
		});
		ll.addView(rl);

		// make seperate line
		ImageView seperateIV4 = new ImageView(parentActivity);
		seperateIV4.setBackgroundColor(getResources().getColor(
				R.color.setting_red));
		seperateIV4.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
						getResources().getDisplayMetrics())));
		ll.addView(seperateIV4); // add seperate line

		// animated text
		showAnimatedText();
	}

	private void loadViewForCode() {
		PullToZoomScrollViewEx scrollView = (PullToZoomScrollViewEx) parentActivity
				.findViewById(R.id.scroll_view);
		// View headView =
		// LayoutInflater.from(parentActivity).inflate(R.layout.activity_setting_head_view,
		// null, false);
		View zoomView = LayoutInflater.from(parentActivity).inflate(
				R.layout.activity_setting_zoom_view, null, false);
		// View contentView =
		// LayoutInflater.from(parentActivity).inflate(R.layout.activity_setting_content_view,
		// null, false);
		scrollView.setZoomView(zoomView);
		scrollView.setParallax(true);
	}

	private void showAnimatedText() {
		// my
		if (Build.VERSION.SDK_INT >= 14) {
			// definitions
			Property<AnimatedColorSpan, Float> ANIMATED_COLOR_SPAN_FLOAT_PROPERTY = new Property<AnimatedColorSpan, Float>(
					Float.class, "ANIMATED_COLOR_SPAN_FLOAT_PROPERTY") {
				@Override
				public void set(AnimatedColorSpan span, Float value) {
					span.setTranslateXPercentage(value);
				}

				@Override
				public Float get(AnimatedColorSpan span) {
					return span.getTranslateXPercentage();
				}
			};

			final TextView textView = (TextView) parentActivity
					.findViewById(R.id.text_head);
			textView.setText("轻小说文库·典藏版");
			String text = textView.getText().toString();

			AnimatedColorSpan span = new AnimatedColorSpan(parentActivity);
			final SpannableString spannableString = new SpannableString(text);
			int start = 0;
			int end = text.length();
			spannableString.setSpan(span, start, end, 0);

			ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(span,
					ANIMATED_COLOR_SPAN_FLOAT_PROPERTY, 0, 100);
			objectAnimator.setEvaluator(new FloatEvaluator());
			objectAnimator
					.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
						@Override
						public void onAnimationUpdate(ValueAnimator animation) {
							textView.setText(spannableString);
						}
					});
			objectAnimator.setInterpolator(new LinearInterpolator());
			objectAnimator.setDuration(DateUtils.MINUTE_IN_MILLIS * 3);
			objectAnimator.setRepeatCount(ValueAnimator.INFINITE);
			objectAnimator.start();
		} else {
			textView_MewX = (TextView) parentActivity
					.findViewById(R.id.text_head);
			textView_MewX.setText("轻小说文库·典藏版");
			highlight_mewx("轻小说文库·典藏版");
		}
	}

	private void highlight_mewx(String query) {
		String text = textView_MewX.getText().toString();
		SpannableString spannableString = new SpannableString(text);

		Pattern pattern = Pattern.compile(query.toLowerCase());
		Matcher matcher = pattern.matcher(text.toLowerCase());
		while (matcher.find()) {
			spannableString.setSpan(new StyleSpan(Typeface.BOLD),
					matcher.start(), matcher.end(), 0);
			spannableString.setSpan(new RainbowSpan(parentActivity),
					matcher.start(), matcher.end(), 0);
		}

		textView_MewX.setText(spannableString);
		textView_MewX = (TextView) parentActivity
				.findViewById(R.id.text_head);
	}

	private static class RainbowSpan extends CharacterStyle implements
			UpdateAppearance {
		private final int[] colors;

		public RainbowSpan(Context context) {
			colors = context.getResources().getIntArray(R.array.rainbow_mewx);
		}

		@Override
		public void updateDrawState(TextPaint paint) {
			paint.setStyle(Paint.Style.FILL);
			Shader shader = new LinearGradient(0, 0, 0, paint.getTextSize()
					* colors.length, colors, null, Shader.TileMode.MIRROR);
			Matrix matrix = new Matrix();
			matrix.setRotate(90);
			shader.setLocalMatrix(matrix);
			paint.setShader(shader);
		}
	}

	private static class AnimatedColorSpan extends CharacterStyle implements
			UpdateAppearance {
		private final int[] colors;
		private Shader shader = null;
		private Matrix matrix = new Matrix();
		private float translateXPercentage = 0;

		public AnimatedColorSpan(Context context) {
			colors = context.getResources().getIntArray(R.array.rainbow_mewx);
		}

		public void setTranslateXPercentage(float percentage) {
			translateXPercentage = percentage;
		}

		public float getTranslateXPercentage() {
			return translateXPercentage;
		}

		@Override
		public void updateDrawState(TextPaint paint) {
			paint.setStyle(Paint.Style.FILL);
			float width = paint.getTextSize() * colors.length;
			if (shader == null) {
				shader = new LinearGradient(0, 0, 0, width, colors, null,
						Shader.TileMode.MIRROR);
			}
			matrix.reset();
			matrix.setRotate(90);
			matrix.postTranslate(width * translateXPercentage, 0);
			shader.setLocalMatrix(matrix);
			paint.setShader(shader);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
        MobclickAgent.onPageStart("Bookshelf");
	}

	@Override
	public void onPause() {
		super.onPause();
        MobclickAgent.onPageEnd("Setting");
	}
}
