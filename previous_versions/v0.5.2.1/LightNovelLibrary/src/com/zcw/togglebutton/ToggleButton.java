package com.zcw.togglebutton;

import org.mewx.lightnovellibrary.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.RectF;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.facebook.rebound.SpringUtil;

/**
 * @author ThinkPad
 *
 */
public class ToggleButton extends View{
	private SpringSystem springSystem;
	private Spring spring ;
	/** */
	private float radius;
	/** 开启颜色*/
	private int onColor = Color.parseColor("#4ebb7f");
	/** 关闭颜色*/
	private int offBorderColor = Color.parseColor("#dadbda");
	/** 灰色带颜色*/
	private int offColor = Color.parseColor("#ffffff");
	/** 手柄颜色*/
	private int spotColor = Color.parseColor("#ffffff");
	/** 边框颜色*/
	private int borderColor = offBorderColor;
	/** 画笔*/
	private Paint paint ;
	/** 开关状态*/
	private boolean toggleOn = false;
	/** 边框大小*/
	private int borderWidth = 2;
	/** 垂直中心*/
	private float centerY;
	/** 按钮的开始和结束位置*/
	private float startX, endX;
	/** 手柄X位置的最小和最大值*/
	private float spotMinX, spotMaxX;
	/**手柄大小 */
	private int spotSize ;
	/** 手柄X位置*/
	private float spotX;
	/** 关闭时内部灰色带高度*/
	private float offLineWidth;
	/** */
	private RectF rect = new RectF();
	
	private OnToggleChanged listener;
	
	private ToggleButton(Context context) {
		super(context);
	}
	public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setup(attrs);
	}
	public ToggleButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup(attrs);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		spring.removeListener(springListener);
	}
	
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		spring.addListener(springListener);
	}

	public void setup(AttributeSet attrs) {
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Style.FILL);
		paint.setStrokeCap(Cap.ROUND);
		
		springSystem = SpringSystem.create();
		spring = springSystem.createSpring();
		spring.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(50, 7));
		
		this.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				toggle();
			}
		});
		
		TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.ToggleButton);
		offBorderColor = typedArray.getColor(R.styleable.ToggleButton_offBorderColor, offBorderColor);
		onColor = typedArray.getColor(R.styleable.ToggleButton_onColor, onColor);
		spotColor = typedArray.getColor(R.styleable.ToggleButton_spotColor, spotColor);
		offColor = typedArray.getColor(R.styleable.ToggleButton_offColor, offColor);
		borderWidth = typedArray.getDimensionPixelSize(R.styleable.ToggleButton_borderWidth, borderWidth);
		typedArray.recycle();
	}
	
	public void toggle() {
		toggleOn = !toggleOn;
		spring.setEndValue(toggleOn ? 1 : 0);
		if(listener != null){
			listener.onToggle(toggleOn);
		}
	}
	
	public void toggleOn() {
		setToggleOn();
		if(listener != null){
			listener.onToggle(toggleOn);
		}
	}
	
	public void toggleOff() {
		setToggleOff();
		if(listener != null){
			listener.onToggle(toggleOn);
		}
	}
	
	/**
	 * 设置显示成打开样式，不会触发toggle事件
	 */
	public void setToggleOn() {
		toggleOn = true;
		spring.setEndValue(toggleOn ? 1 : 0);
	}
	
	/**
	 * 设置显示成关闭样式，不会触发toggle事件
	 */
	public void setToggleOff() {
		toggleOn = false;
		spring.setEndValue(toggleOn ? 1 : 0);
	}
	
	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		
		final int width = getWidth();
		final int height = getHeight();
		radius = Math.min(width, height) * 0.5f;
		centerY = radius;
		startX = radius;
		endX = width - radius;
		spotMinX = startX + borderWidth;
		spotMaxX = endX - borderWidth;
		spotSize = height - 4 * borderWidth;
		spotX = spotMinX;
		offLineWidth = 0;
	}
	
	
	SimpleSpringListener springListener = new SimpleSpringListener(){
		@Override
		public void onSpringUpdate(Spring spring) {
			final double value = spring.getCurrentValue();
			
			final float mapToggleX = (float) SpringUtil.mapValueFromRangeToRange(value, 0, 1, spotMinX, spotMaxX);
			spotX = mapToggleX;
			
			float mapOffLineWidth = (float) SpringUtil.mapValueFromRangeToRange(1 - value, 0, 1, 10, spotSize);
			
			offLineWidth = mapOffLineWidth;
			
			final int fb = Color.blue(onColor);
			final int fr = Color.red(onColor);
			final int fg = Color.green(onColor);
			
			final int tb = Color.blue(offBorderColor);
			final int tr = Color.red(offBorderColor);
			final int tg = Color.green(offBorderColor);
			
			int sb = (int) SpringUtil.mapValueFromRangeToRange(1 - value, 0, 1, fb, tb);
			int sr = (int) SpringUtil.mapValueFromRangeToRange(1 - value, 0, 1, fr, tr);
			int sg = (int) SpringUtil.mapValueFromRangeToRange(1 - value, 0, 1, fg, tg);
			
			sb = clamp(sb, 0, 255);
			sr = clamp(sr, 0, 255);
			sg = clamp(sg, 0, 255);
			
			borderColor = Color.rgb(sr, sg, sb);
			
			postInvalidate();
		}
	};

	private int clamp(int value, int low, int high) {
		return Math.min(Math.max(value, low), high);
	}

	
	@Override
	public void draw(Canvas canvas) {
		
		/*
		final int height = getHeight();
		//绘制背景（边框）
		paint.setStrokeWidth(height);
		paint.setColor(borderColor);
		canvas.drawLine(startX, centerY, endX, centerY, paint);
		//绘制灰色带
		if(offLineWidth > 0){
			paint.setStrokeWidth(offLineWidth);
			paint.setColor(offColor);
			canvas.drawLine(spotX, centerY, endX, centerY, paint);
		}
		//spot的边框
		paint.setStrokeWidth(height);
		paint.setColor(borderColor);
		canvas.drawLine(spotX - 1, centerY, spotX + 1.1f, centerY, paint);
		//spot
		paint.setStrokeWidth(spotSize);
		paint.setColor(spotColor);
		canvas.drawLine(spotX, centerY, spotX + 0.1f, centerY, paint);
		*/
		
		
		//
		rect.set(0, 0, getWidth(), getHeight());
		paint.setColor(borderColor);
		canvas.drawRoundRect(rect, radius, radius, paint);
		
		if(offLineWidth > 0){
			final float cy = offLineWidth * 0.5f;
			rect.set(spotX - cy, centerY - cy, endX + cy, centerY + cy);
			paint.setColor(offColor);
			canvas.drawRoundRect(rect, cy, cy, paint);
		}
		
		rect.set(spotX - 1 - radius, centerY - radius, spotX + 1.1f + radius, centerY + radius);
		paint.setColor(borderColor);
		canvas.drawRoundRect(rect, radius, radius, paint);
		
		final float spotR = spotSize * 0.5f;
		rect.set(spotX - spotR, centerY - spotR, spotX + spotR, centerY + spotR);
		paint.setColor(spotColor);
		canvas.drawRoundRect(rect, spotR, spotR, paint);
		
	}
	
	
	/**
	 * @author ThinkPad
	 *
	 */
	public interface OnToggleChanged{
		/**
		 * @param on
		 */
		public void onToggle(boolean on);
	}


	public void setOnToggleChanged(OnToggleChanged onToggleChanged) {
		listener = onToggleChanged;
	}
	
}
