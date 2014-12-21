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
import com.special.ResideMenu.ResideMenu;

import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

@TargetApi(14)
public class SettingFragment extends Fragment {
	public static String fromid = "bookshelf";
	private View parentView;
	private ResideMenu resideMenu;
	private ProgressDialog pDialog;
	private TextView textView_Wenku8, textView_MewX;

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
		parentView = inflater
				.inflate(R.layout.activity_about, container, false);
		setUpViews();

		// set the two button on the title bar
		((TextView) getActivity().findViewById(R.id.textTitle))
				.setText(getResources().getString(R.string.about));
		((ImageView) getActivity().findViewById(R.id.btnMenu))
				.setVisibility(View.VISIBLE);
		((ImageView) getActivity().findViewById(R.id.btnEdit))
				.setVisibility(View.GONE);
		getActivity().findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
					}
				});

		// Wenku8
		textView_Wenku8 = (TextView) parentView
				.findViewById(R.id.textView_resource_provider);
		highlight("Wenku8.cn");

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
			
			final TextView textView = (TextView) parentView
					.findViewById(R.id.textView_developer);
			String text = textView.getText().toString();

			AnimatedColorSpan span = new AnimatedColorSpan(getActivity());
			final SpannableString spannableString = new SpannableString(text);
			int start = text.toLowerCase().indexOf("mewx");
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
		}
		else {
			textView_MewX = (TextView) parentView
					.findViewById(R.id.textView_developer);
			highlight_mewx("MewX");
		}

		return parentView;
	}

	private void setUpViews() {
		MainActivity parentActivity = (MainActivity) getActivity();
		resideMenu = parentActivity.getResideMenu();

		// Button action
		// parentView.findViewById(R.id.btn_open_menu).setOnClickListener(
		// new View.OnClickListener() {
		// @Override
		// public void onClick(View view) {
		// resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
		// }
		// });

		resideMenu.setMenuListener(menuListener);
		resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

		// add gesture operation's ignored views
		// FrameLayout ignored_view = (FrameLayout) parentView
		// .findViewById(R.id.ignored_view);
		// resideMenu.addIgnoredView(ignored_view);
	}

	private void highlight(String query) {
		String text = textView_Wenku8.getText().toString();
		SpannableString spannableString = new SpannableString(text);

		Pattern pattern = Pattern.compile(query.toLowerCase());
		Matcher matcher = pattern.matcher(text.toLowerCase());
		while (matcher.find()) {
			spannableString.setSpan(new StyleSpan(Typeface.BOLD),
					matcher.start(), matcher.end(), 0);
			spannableString.setSpan(new RainbowSpan(getActivity()),
					matcher.start(), matcher.end(), 0);
		}

		textView_Wenku8.setText(spannableString);
		textView_Wenku8 = (TextView) parentView
				.findViewById(R.id.textView_developer);
	}
	
	private void highlight_mewx(String query) {
		String text = textView_MewX.getText().toString();
		SpannableString spannableString = new SpannableString(text);

		Pattern pattern = Pattern.compile(query.toLowerCase());
		Matcher matcher = pattern.matcher(text.toLowerCase());
		while (matcher.find()) {
			spannableString.setSpan(new StyleSpan(Typeface.BOLD),
					matcher.start(), matcher.end(), 0);
			spannableString.setSpan(new RainbowSpan(getActivity()),
					matcher.start(), matcher.end(), 0);
		}

		textView_MewX.setText(spannableString);
		textView_MewX = (TextView) parentView
				.findViewById(R.id.textView_developer);
	}

	private static class RainbowSpan extends CharacterStyle implements
			UpdateAppearance {
		private final int[] colors;

		public RainbowSpan(Context context) {
			colors = context.getResources().getIntArray(R.array.rainbow);
		}

		@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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
			colors = context.getResources().getIntArray(R.array.rainbow);
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

}
