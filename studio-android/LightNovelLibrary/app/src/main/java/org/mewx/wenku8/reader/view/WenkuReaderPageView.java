package org.mewx.wenku8.reader.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.text.TextPaint;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageSize;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.ViewImageDetailActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.api.Wenku8Error;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.setting.WenkuReaderSettingV1;
import org.mewx.wenku8.util.LightTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by MewX on 2015/7/8.
 *
 * Implement whole view of page, and use full screen page size.
 *
 * Default Elements:
 *  - Top: ChapterTitle, WIFI/DC
 *  - Bot: Battery, Paragraph/All, CurrentTime
 *
 * Click Elements:
 *  - Top: NovelTitle
 *  - Bot: ToolBar
 */
public class WenkuReaderPageView extends View {
    private static final String TAG = WenkuReaderPageView.class.getSimpleName();

    // enum
    public enum LOADING_DIRECTION {
        FORWARDS, // go to next page
        CURRENT, // get this page
        BACKWARDS // go to previous page
    }

    // class
    private class BitmapInfo {
        int idxLineInfo;
        int width, height;
        int x_beg, y_beg;
        Bitmap bm;
    }
    List<BitmapInfo> bitmapInfoList;

    // core variables
    static private boolean inDayMode = true;
    final static private String sampleText = "轻";
    static private WenkuReaderLoader mLoader;
    static private WenkuReaderSettingV1 mSetting;
    static private int pxLineDistance, pxParagraphDistance, pxPageEdgeDistance, pxWidgetHeight;
    static private Point screenSize; // Screen real size.
    static private Pair<Point, Point> screenDrawArea; // The area we want to draw text/images in.
    private Point textAreaSize; // TODO: remove this variable.
    static private Typeface typeface;
    static private TextPaint textPaint, widgetTextPaint;
    static private int fontHeight, widgetFontHeihgt;
    private int lineCount;

    // background
    static private final Random random = new Random();
    static private Bitmap bmBackgroundYellow;
    private static final int[] bmTextureYellowResourceIds = new int[]{
            R.drawable.reader_bg_yellow1,
            R.drawable.reader_bg_yellow2,
            R.drawable.reader_bg_yellow3,
    };
    static private BitmapDrawable bmdBackground;
    static private boolean isBackgroundSet = false;
    private final WenkuReaderPaginator paginator;

    // view components (battery, page number, etc.)

    static public boolean getInDayMode() {
        return inDayMode;
    }

    static public boolean switchDayMode() {
        inDayMode = !inDayMode;
        return inDayMode;
    }

    /**
     * Set view static variables, before first onDraw()
     * @param wrl loader
     * @param wrs setting
     */
    static public void setViewComponents(WenkuReaderLoader wrl, WenkuReaderSettingV1 wrs, boolean forceMode) {
        mLoader = wrl;
        mSetting = wrs;
        pxLineDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getLineDistance()); // 行间距
        pxParagraphDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getParagraphDistance()); // 段落间距
        pxPageEdgeDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getPageEdgeDistance()); // 页面边距

        // calc general var
        try {
            if(mSetting.getUseCustomFont()) typeface = Typeface.createFromFile(mSetting.getCustomFontPath()); // custom font
        }
        catch (Exception e) {
            Toast.makeText(MyApp.getContext(), e + "\n可能的原因有：字体文件不在内置SD卡；内存太小字体太大，请使用简体中文字体，而不是CJK或GBK，谢谢，此功能为试验性功能；", Toast.LENGTH_SHORT).show();
        }
        textPaint = new TextPaint();
        textPaint.setColor(getInDayMode() ? mSetting.fontColorDark : mSetting.fontColorLight);
        textPaint.setTextSize(LightTool.sp2px(MyApp.getContext(), (float) mSetting.getFontSize()));
        if(mSetting.getUseCustomFont() && typeface != null) textPaint.setTypeface(typeface);
        textPaint.setAntiAlias(true);
        fontHeight = (int) textPaint.measureText(sampleText); // in "px"
        widgetTextPaint = new TextPaint();
        widgetTextPaint.setColor(getInDayMode() ? mSetting.fontColorDark : mSetting.fontColorLight);
        widgetTextPaint.setTextSize(LightTool.sp2px(MyApp.getContext(), (float) mSetting.widgetTextSize));
        widgetTextPaint.setAntiAlias(true);
        widgetFontHeihgt = (int) textPaint.measureText(sampleText);

        // Update widget height.
        pxWidgetHeight = LightTool.dip2px(MyApp.getContext(), mSetting.widgetHeight); // default.
        pxWidgetHeight = 3 * widgetFontHeihgt / 2; // 2/3 font height

        // load bitmap
        if(forceMode || !isBackgroundSet) {
            screenSize = LightTool.getRealScreenSize(MyApp.getContext());

            if(mSetting.getPageBackgroundType() == WenkuReaderSettingV1.PAGE_BACKGROUND_TYPE.CUSTOM) {
                try {
                    bmBackgroundYellow = BitmapFactory.decodeFile(mSetting.getPageBackgroundCustomPath());
                } catch (OutOfMemoryError oome) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        bmBackgroundYellow = BitmapFactory.decodeFile(mSetting.getPageBackgroundCustomPath(), options);
                    } catch(Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
                bmdBackground = null;
            }
            if(mSetting.getPageBackgroundType() == WenkuReaderSettingV1.PAGE_BACKGROUND_TYPE.SYSTEM_DEFAULT || bmBackgroundYellow == null) {
                // use system default
                bmBackgroundYellow = BitmapFactory.decodeResource(MyApp.getContext().getResources(), R.drawable.reader_bg_yellow_edge);
                Bitmap bmTexturePattern = BitmapFactory.decodeResource(MyApp.getContext().getResources(),
                        bmTextureYellowResourceIds[random.nextInt(bmTextureYellowResourceIds.length)]);
                bmdBackground = new BitmapDrawable(MyApp.getContext().getResources(), bmTexturePattern);
                bmdBackground.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                bmdBackground.setBounds(0, 0, screenSize.x, screenSize.y);
            }
            isBackgroundSet = true;
        }
    }

    /**
     * Reset text color, to fit day/night mode.
     * If textPaint is null, then do nothing.
     */
    static public void resetTextColor() {
        textPaint.setColor(getInDayMode() ? mSetting.fontColorDark : mSetting.fontColorLight);
        widgetTextPaint.setColor(getInDayMode() ? mSetting.fontColorDark : mSetting.fontColorLight);
    }

    /**
     * Calculate the actual area for drawing.
     * @return the two points forming a rectangle that can actually hold content.
     *         The two points are: top left point and bottom right point.
     */
    private Pair<Point, Point> getScreenLayout() {
        int statusBarHeight = LightTool.getStatusBarHeightValue(MyApp.getContext());
        int navBarHeight = LightTool.getNavigationBarHeightValue(MyApp.getContext());

        // Add cutting positions.
        Rect cutout = LightTool.getDisplayCutout();
        int top = pxPageEdgeDistance + Math.max(cutout.top, statusBarHeight);
        int left = pxPageEdgeDistance + cutout.left;
        int right = pxPageEdgeDistance + cutout.right;
        int bottom = pxPageEdgeDistance + pxWidgetHeight + cutout.bottom;

        Point topLeft = new Point(left, top);
        Point bottomRight = new Point(screenSize.x - right, screenSize.y - bottom);
        return new Pair<>(topLeft, bottomRight);
    }

    /**
     * This function init the view class。
     * Notice: (-1, -1), (-1, 0), (0, -1) means first page.
     * @param context current context, should be WenkuReaderActivity
     * @param lineIndex if FORWARDS, this is the last index of last page;
     *              if CURRENT, this is the first index of this page;
     *              if BACKWARDS, this is the first index of last page;
     * @param directionForward get next or get previous
     */
    public WenkuReaderPageView(Context context, int lineIndex, int wordIndex, LOADING_DIRECTION directionForward) {
        super(context);
        Log.d("MewX", "-- view: construct my");
        bitmapInfoList = new ArrayList<>();
        mLoader.setCurrentIndex(lineIndex);

        // first.x = left
        // first.y = top
        // second.x = screen.x - right
        // second.y = screen.y - bottom
        screenDrawArea = getScreenLayout();

        // get environmental vars, use actual layout size: width x height
        textAreaSize = new Point(screenDrawArea.second.x - screenDrawArea.first.x,
                screenDrawArea.second.y - screenDrawArea.first.y);

        paginator = new WenkuReaderPaginator(mLoader,
                text -> textPaint.measureText(text),
                textAreaSize.x, textAreaSize.y, fontHeight,
                pxLineDistance, pxParagraphDistance);

        // save vars, calc all ints
        switch (directionForward) {
            case FORWARDS:
                if (wordIndex + 1 < mLoader.getCurrentStringLength()) {
                    paginator.setPageStart(
                            lineIndex,
                            /* wordIndex= */ lineIndex == 0 && wordIndex == 0 ? 0 : wordIndex + 1);
                } else if (lineIndex + 1 < mLoader.getElementCount()) {
                    paginator.setPageStart(lineIndex + 1, /* wordIndex= */ 0);
                } else {
                    Log.d("MewX", "-- view: end construct A, just return");
                    return;
                }
                paginator.calcFromFirst();
                break;

            case CURRENT:
                paginator.setPageStart(lineIndex, wordIndex);
                paginator.calcFromFirst();
                break;

            case BACKWARDS:
                // fit first and last
                if (wordIndex > 0) {
                    // firstLineIndex firstWordIndex; and last values changeable
                    paginator.setPageEnd(lineIndex, wordIndex - 1);
                } else if (lineIndex > 0) {
                    paginator.setPageEnd(
                            lineIndex - 1,
                            /* wordIndex= */ mLoader.getStringLength(paginator.getLastLineIndex()) - 1);
                }
                paginator.calcFromLast();
                break;
        }

        for (LineInfo li : paginator.getLineInfoList()) {
            Log.d("MewX", "get: " + li.text());
        }
    }

    private void drawBackground(Canvas canvas) {
        if(getInDayMode()) {
            // day
            if(bmdBackground != null)
                bmdBackground.draw(canvas);
            if(bmBackgroundYellow.getWidth() != screenSize.x || bmBackgroundYellow.getHeight() != screenSize.y)
                bmBackgroundYellow = Bitmap.createScaledBitmap(bmBackgroundYellow, screenSize.x, screenSize.y, true);
            canvas.drawBitmap(bmBackgroundYellow, 0, 0, null);
        }
        else {
            // night
            Paint paintBackground = new Paint();
            paintBackground.setColor(mSetting.bgColorDark);
            canvas.drawRect(0, 0, screenSize.x, screenSize.y, paintBackground);
        }
    }

    private void drawWidgets(Canvas canvas) {
        canvas.drawText(mLoader.getChapterName(), screenDrawArea.first.x, screenDrawArea.second.y + widgetFontHeihgt, widgetTextPaint);
        String percentage = "( " + (paginator.getLastLineIndex() + 1) * 100 / mLoader.getElementCount() + "% )";
        final int textWidth = (int) widgetTextPaint.measureText(percentage);
        canvas.drawText(percentage, screenDrawArea.second.x - textWidth, screenDrawArea.second.y + widgetFontHeihgt, widgetTextPaint);
    }

    private void drawContent(Canvas canvas) {
        int heightSum = screenDrawArea.first.y + fontHeight; // The baseline (i.e. y).
        for(int i = 0; i < paginator.getLineInfoList().size(); i++) {
            final LineInfo li = paginator.getLineInfoList().get(i);
            if( i != 0 ) {
                if(li.text().length() > 2 && li.text().substring(0, 2).equals("　　")) {
                    heightSum += pxParagraphDistance;
                }
                else {
                    heightSum += pxLineDistance;
                }
            }

            Log.d(WenkuReaderPageView.class.getSimpleName(), "draw: " + li.text());
            if(li.type() == WenkuReaderLoader.ElementType.TEXT) {
                canvas.drawText( li.text(), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                heightSum += fontHeight;
            } else if(li.type() == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT){
                if (bitmapInfoList == null) {
                    // TODO: fix this magic number 21.
                    canvas.drawText("Unexpected array: " + li.text().substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                    continue;
                }

                BitmapInfo bi = null;
                for (BitmapInfo bitmapInfo : bitmapInfoList) {
                    if (bitmapInfo.idxLineInfo == i) {
                        bi = bitmapInfo;
                        break;
                    }
                }

                if (bi == null) {
                    // not found, new load task
                    // TODO: fix this magic number 21.
                    canvas.drawText("正在加载图片：" + li.text().substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                    bi = new BitmapInfo();
                    bi.idxLineInfo = i;
                    bi.x_beg = screenDrawArea.first.x;
                    bi.y_beg = screenDrawArea.first.y;
                    bi.height = textAreaSize.y;
                    bi.width = textAreaSize.x;
                    bitmapInfoList.add(0, bi);

                    AsyncLoadImage ali = new AsyncLoadImage();
                    ali.execute(bitmapInfoList.get(0));
                } else {
                    if (bi.bm == null) {
                        // TODO: fix this magic number 21.
                        canvas.drawText("正在加载图片：" + li.text().substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                    } else {
                        int new_x = (screenDrawArea.second.x - screenDrawArea.first.x - bi.width) / 2 + bi.x_beg;
                        int new_y = (screenDrawArea.second.y - screenDrawArea.first.y - bi.height) / 2 + bi.y_beg;
                        canvas.drawBitmap(bi.bm, new_x, new_y, new Paint());
                    }
                }
            } else {
                // TODO: fix this magic number 21.
                canvas.drawText("（！请先用旧引擎浏览）图片" + li.text().substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mSetting == null || mLoader == null) return;

        // Draw everything.
        Log.d(WenkuReaderPageView.class.getSimpleName(), "onDraw()");
        drawBackground(canvas);
        drawWidgets(canvas);
        drawContent(canvas);
    }

    public int getFirstLineIndex() {
        return paginator.getFirstLineIndex();
    }

    public int getFirstWordIndex() {
        return paginator.getFirstWordIndex();
    }

    public int getLastLineIndex() {
        return paginator.getLastLineIndex();
    }

    /**
     * @return last paragraph's last word's index
     */
    public int getLastWordIndex() {
        return paginator.getLastWordIndex();
    }

    private class AsyncLoadImage extends AsyncTask<BitmapInfo, Integer, Wenku8Error.ErrorCode> {

        @Override
        protected Wenku8Error.ErrorCode doInBackground(BitmapInfo... params) {
            // Make an alias for the bitmap info.
            BitmapInfo bitmapInfo = params[0];

            String imgFileName = GlobalConfig.generateImageFileNameByURL(paginator.getLineInfoList().get(bitmapInfo.idxLineInfo).text());
            if(GlobalConfig.getAvailableNovelContentImagePath(imgFileName) == null) {
                if (!GlobalConfig.saveNovelContentImage(paginator.getLineInfoList().get(bitmapInfo.idxLineInfo).text())) {
                    return Wenku8Error.ErrorCode.NETWORK_ERROR;
                }

                // Double check if the image exists in local storage.
                if (GlobalConfig.getAvailableNovelContentImagePath(imgFileName) == null) {
                    return Wenku8Error.ErrorCode.STORAGE_ERROR;
                }

                // The image should be downloaded.
                imgFileName = GlobalConfig.generateImageFileNameByURL(paginator.getLineInfoList().get(bitmapInfo.idxLineInfo).text());
            }

            ImageSize targetSize = new ImageSize(bitmapInfo.width, bitmapInfo.height); // result Bitmap will be fit to this size
            bitmapInfo.bm = ImageLoader.getInstance().loadImageSync("file://" + GlobalConfig.getAvailableNovelContentImagePath(imgFileName), targetSize);
            if (bitmapInfo.bm == null) {
                return Wenku8Error.ErrorCode.IMAGE_LOADING_ERROR;
            }

            int width = bitmapInfo.bm.getWidth(), height = bitmapInfo.bm.getHeight();
            if (bitmapInfo.height / (float) bitmapInfo.width > height / (float) width) {
                // fit width
                float percentage = (float)height / width;
                bitmapInfo.height = (int) (bitmapInfo.width * percentage);
            }
            else {
                // fit height
                float percentage = (float)width / height;
                bitmapInfo.width = (int) (bitmapInfo.height * percentage);
            }
            bitmapInfo.bm = Bitmap.createScaledBitmap(bitmapInfo.bm, bitmapInfo.width, bitmapInfo.height, true);
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            if (errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                WenkuReaderPageView.this.postInvalidate();
            } else {
                Log.e(TAG, "onPostExecute: image cannot be loaded " + errorCode.toString());
                Toast.makeText(getContext(), errorCode.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void watchImageDetailed(Activity activity) {
        if(bitmapInfoList == null || bitmapInfoList.isEmpty() || bitmapInfoList.get(0).bm == null) {
            Toast.makeText(getContext(), getResources().getString(R.string.reader_view_image_no_image), Toast.LENGTH_SHORT).show();
        }
        else {
            Intent intent = new Intent(activity, ViewImageDetailActivity.class);
            intent.putExtra("path", GlobalConfig.getAvailableNovelContentImagePath(GlobalConfig.generateImageFileNameByURL(paginator.getLineInfoList().get(bitmapInfoList.get(0).idxLineInfo).text())));
            activity.startActivity(intent);
            activity.overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
        }
    }
}
