package org.mewx.lightnovellibrary.activity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.mewx.lightnovellibrary.R;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.util.Property;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.umeng.analytics.MobclickAgent;

@TargetApi(14)
public class AboutActivity extends SwipeBackActivity {

	private Activity parentActivity = null;

	private ProgressDialog pDialog;
	private TextView textView_Wenku8, textView_MewX;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

		// get parentActivity
		parentActivity = this;

		// set the two button on the title bar
		((ImageView) findViewById(R.id.btnMenu))
				.setImageResource(R.drawable.ic_back);
		((ImageView) findViewById(R.id.btnMenu)).setVisibility(View.VISIBLE);
		((ImageView) parentActivity.findViewById(R.id.btnEdit))
				.setVisibility(View.GONE);
		findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onBackPressed();
					}
				});

		// final ToggleButton toggleBtn = (ToggleButton) parentActivity
		// .findViewById(R.id.switcher);
		// // 开关切换事件
		// toggleBtn.setOnToggleChanged(new OnToggleChanged() {
		// @Override
		// public void onToggle(boolean on) {
		// // 切换开关
		// //toggleBtn.toggle();
		// // if (on)
		// // toggleBtn.setToggleOn();
		// // else
		// // toggleBtn.setToggleOff();
		// }
		// });

		// Wenku8
		textView_Wenku8 = (TextView) parentActivity
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

			final TextView textView = (TextView) parentActivity
					.findViewById(R.id.textView_developer);
			String text = textView.getText().toString();

			AnimatedColorSpan span = new AnimatedColorSpan(parentActivity);
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
		} else {
			textView_MewX = (TextView) parentActivity
					.findViewById(R.id.textView_developer);
			highlight_mewx("MewX");
		}

		return;
	}

	private void highlight(String query) {
		String text = textView_Wenku8.getText().toString();
		SpannableString spannableString = new SpannableString(text);

		Pattern pattern = Pattern.compile(query.toLowerCase());
		Matcher matcher = pattern.matcher(text.toLowerCase());
		while (matcher.find()) {
			spannableString.setSpan(new StyleSpan(Typeface.BOLD),
					matcher.start(), matcher.end(), 0);
			spannableString.setSpan(new RainbowSpan(parentActivity),
					matcher.start(), matcher.end(), 0);
		}

		textView_Wenku8.setText(spannableString);
		textView_Wenku8 = (TextView) parentActivity
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
			spannableString.setSpan(new RainbowSpan(parentActivity),
					matcher.start(), matcher.end(), 0);
		}

		textView_MewX.setText(spannableString);
		textView_MewX = (TextView) parentActivity
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

    public void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    public void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}
}
