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
import android.os.Build;
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
import org.mewx.wenku8.global.api.Wenku8Error;
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
    // enum
    public enum LOADING_DIRECTION {
        FORWARDS, // go to next page
        CURRENT, // get this page
        BACKWARDS // go to previous page
    }

    // class
    private class LineInfo {
        WenkuReaderLoader.ElementType type;
        String text;
    }
    List<LineInfo> lineInfoList;
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
    static private Bitmap bmBackgroundYellow, bmTextureYellow[];
    static private BitmapDrawable bmdBackground;
    static private Random random = new Random();
    static private boolean isBackgroundSet = false;

    // vars
    private int firstLineIndex;
    private int firstWordIndex;
    private int lastLineIndex;
    private int lastWordIndex; // last paragraph's last word's index

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
            Toast.makeText(MyApp.getContext(), e.toString() + "\n可能的原因有：字体文件不在内置SD卡；内存太小字体太大，请使用简体中文字体，而不是CJK或GBK，谢谢，此功能为试验性功能；", Toast.LENGTH_SHORT).show();
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
            if(Build.VERSION.SDK_INT < 19) {
                screenSize.y -= LightTool.getStatusBarHeightValue(MyApp.getContext());
            }

            if(mSetting.getPageBackgroundType() == WenkuReaderSettingV1.PAGE_BACKGROUND_TYPE.CUSTOM) {
                try {
                    bmBackgroundYellow = BitmapFactory.decodeFile(mSetting.getPageBackgrounCustomPath());
                } catch (OutOfMemoryError oome) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        bmBackgroundYellow = BitmapFactory.decodeFile(mSetting.getPageBackgrounCustomPath(), options);
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
                bmTextureYellow = new Bitmap[3];
                bmTextureYellow[0] = BitmapFactory.decodeResource(MyApp.getContext().getResources(), R.drawable.reader_bg_yellow1);
                bmTextureYellow[1] = BitmapFactory.decodeResource(MyApp.getContext().getResources(), R.drawable.reader_bg_yellow2);
                bmTextureYellow[2] = BitmapFactory.decodeResource(MyApp.getContext().getResources(), R.drawable.reader_bg_yellow3);

                bmdBackground = new BitmapDrawable(MyApp.getContext().getResources(), bmTextureYellow[random.nextInt(bmTextureYellow.length)]);
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

        if (Build.VERSION.SDK_INT < 19) {
            // Status bar didn't support transparent.
            top -= statusBarHeight;
            // Navigation bar didn't support transparent.
            bottom += navBarHeight;
        }

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
        lineInfoList = new ArrayList<>();
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

        // save vars, calc all ints
        switch (directionForward) {
            case FORWARDS:
                if(wordIndex + 1 < mLoader.getCurrentStringLength()) {
                    firstLineIndex = lineIndex;
                    if(lineIndex == 0 && wordIndex == 0)
                        firstWordIndex = 0;
                    else
                        firstWordIndex = wordIndex + 1;
                }
                else if(lineIndex + 1 < mLoader.getElementCount()){
                    firstLineIndex = lineIndex + 1;
                    firstWordIndex = 0;
                }
                else {
                    Log.d("MewX", "-- view: end construct A, just return");
                    return;
                }
                mLoader.setCurrentIndex(firstLineIndex);
                calcFromFirst();
                break;

            case CURRENT:
                firstLineIndex = lineIndex;
                firstWordIndex = wordIndex;
                mLoader.setCurrentIndex(firstLineIndex);
                calcFromFirst();
                break;

            case BACKWARDS:
                // fit first and last
                if(wordIndex > 0) {
                    lastLineIndex = lineIndex;
                    lastWordIndex = wordIndex - 1;
                }
                else if(lineIndex > 0) {
                    lastLineIndex = lineIndex - 1;
                    lastWordIndex = mLoader.getStringLength(lastLineIndex) - 1;
                }

                // firstLineIndex firstWordIndex; and last values changeable
                mLoader.setCurrentIndex(lastLineIndex);
                calcFromLast();
                break;
        }

        for(LineInfo li : lineInfoList)
            Log.d("MewX", "get: " + li.text);

    }

    /**
     * Calc page from first to last.
     * firstLineIndex & firstWordIndex set.
     */
    private void calcFromFirst() {
        int widthSum = 0;
        int heightSum = fontHeight;
        StringBuilder tempText = new StringBuilder();

        Log.d("MewX", "firstLineIndex = " + firstLineIndex + "; firstWordIndex = " + firstWordIndex);
        for(int curLineIndex = firstLineIndex, curWordIndex = firstWordIndex; curLineIndex < mLoader.getElementCount(); ) {
            // init paragraph head vars
            if(curWordIndex == 0 && mLoader.getCurrentType() == WenkuReaderLoader.ElementType.TEXT) {
                // leading space
                widthSum = 2 * fontHeight;
                tempText = new StringBuilder("　　");
            }
            else if(mLoader.getCurrentType() == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                if(lineInfoList.size() != 0) {
                    // end a page first
                    lastLineIndex = mLoader.getCurrentIndex() - 1;
                    mLoader.setCurrentIndex(lastLineIndex);
                    lastWordIndex = mLoader.getCurrentStringLength() - 1;
                    break;
                }

                // one image on page
                lastLineIndex = firstLineIndex = mLoader.getCurrentIndex();
                firstWordIndex = 0;
                lastWordIndex = mLoader.getCurrentStringLength() - 1;
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.IMAGE_DEPENDENT;
                li.text = mLoader.getCurrentAsString();
                lineInfoList.add(li);
                break;
            }

            // get a record of line
            if(mLoader.getCurrentAsString() == null || mLoader.getCurrentStringLength() == 0) {
                Log.d("MewX", "empty string! in " + curLineIndex + "(" + curWordIndex + ")");
                curWordIndex = 0;
                if(curLineIndex >= mLoader.getElementCount()) {
                    // out of bounds
                    break;
                }
                mLoader.setCurrentIndex(++ curLineIndex);
                continue;
            }
            String temp = mLoader.getCurrentAsString().charAt(curWordIndex) + "";
            int tempWidth = (int) textPaint.measureText(temp);

            // Line full?
            if(widthSum + tempWidth > textAreaSize.x) {
                // wrap line, save line
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.TEXT;
                li.text = tempText.toString();
                lineInfoList.add(li);
                heightSum += pxLineDistance;

                // change vars for next line
                if(heightSum + fontHeight > textAreaSize.y) {
                    // reverse one index
                    if(curWordIndex > 0) {
                        lastLineIndex = curLineIndex;
                        lastWordIndex = curWordIndex - 1;
                    }
                    else if(curLineIndex > 0) {
                        mLoader.setCurrentIndex(-- curLineIndex);
                        lastLineIndex = curLineIndex;
                        lastWordIndex = mLoader.getCurrentStringLength() - 1;
                    }
                    else {
                        lastLineIndex = lastWordIndex = 0;
                    }
                    break; // height overflow
                }

                // height acceptable
                tempText = new StringBuilder(temp);
                widthSum = tempWidth;
                heightSum += fontHeight;
            }
            else {
                tempText.append(temp);
                widthSum += tempWidth;
            }

            // String end?
            if(curWordIndex + 1 >= mLoader.getCurrentStringLength()) {
                // next paragraph, wrap line
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.TEXT;
                li.text = tempText.toString();
                lineInfoList.add(li);
                heightSum += pxParagraphDistance;

                // height not acceptable
                if(heightSum + fontHeight > textAreaSize.y) {
                    lastLineIndex = mLoader.getCurrentIndex();
                    lastWordIndex = mLoader.getCurrentStringLength() - 1;
                    break; // height overflow
                }

                // height acceptable
                heightSum += fontHeight;
                widthSum = 0;
                tempText = new StringBuilder();
                curWordIndex = 0;
                if(curLineIndex + 1 >= mLoader.getElementCount()) {
                    // out of bounds
                    lastLineIndex = curLineIndex;
                    lastWordIndex = mLoader.getCurrentStringLength() - 1;
                    break;
                }
                mLoader.setCurrentIndex(++ curLineIndex);
            }
            else {
                curWordIndex ++;
            }
        }
    }

    /**
     * Calc page from last to first
     * lastLineIndex & lastWordIndex set.
     */
    private void calcFromLast() {

        int heightSum = 0;
        boolean isFirst = true;
        mLoader.setCurrentIndex(lastLineIndex);

        LineLoop:
        for(int curLineIndex = lastLineIndex, curWordIndex = lastWordIndex; curLineIndex >= 0; ) {
            // calc curLine to curWord(contained), make a String list
            WenkuReaderLoader.ElementType curType = mLoader.getCurrentType();
            String curString = mLoader.getCurrentAsString();

            // special to image
            if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT && lineInfoList.size() != 0) {
                Log.d("MewX", "jump 1");
                firstLineIndex = curLineIndex + 1;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList = new ArrayList<>();
                calcFromFirst();
                break;
            }
            else if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                // one image on page
                lastLineIndex = firstLineIndex = mLoader.getCurrentIndex();
                firstWordIndex = 0;
                lastWordIndex = mLoader.getCurrentStringLength() - 1;
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.IMAGE_DEPENDENT;
                li.text = mLoader.getCurrentAsString();
                lineInfoList.add(li);
                break;
            }

            int tempWidth = 0;
            List<LineInfo> curList = new ArrayList<>();
            String temp = "";
            for(int i = 0; i < curString.length(); ) {
                if(i == 0) {
                    tempWidth += fontHeight + fontHeight;
                    temp = "　　";
                }

                String c = curString.charAt(i) + "";
                int width = (int) textPaint.measureText(c);
                if(tempWidth + width > textAreaSize.x) {
                    // save line to next
                    LineInfo li = new LineInfo();
                    li.type = WenkuReaderLoader.ElementType.TEXT;
                    li.text = temp;
                    curList.add(li);

                    // fit needs
                    if(i >= curWordIndex) break;

                    // goto next round
                    tempWidth = 0;
                    temp = "";
                    continue;
                }
                else {
                    temp = temp + c;
                    tempWidth += width;
                    i ++;
                }

                // string end
                if(i == curString.length()) {
                    LineInfo li = new LineInfo();
                    li.type = WenkuReaderLoader.ElementType.TEXT;
                    li.text = temp;
                    curList.add(li);
                }
            }

            // reverse to add to lineInfoList, full to break, image to do calcFromFirst then break
            for(int i = curList.size() - 1; i >= 0; i --) {
                if(isFirst)
                    isFirst = false;
                else if(i == curList.size() - 1)
                    heightSum += pxParagraphDistance;
                else
                    heightSum += pxLineDistance;

                heightSum += fontHeight;
                if(heightSum > textAreaSize.y) {
                    // calc first index
                    int indexCount = -2;
                    for(int j = 0; j <= i; j ++) indexCount += curList.get(j).text.length();
                    firstLineIndex = curLineIndex;
                    firstWordIndex = indexCount + 1;

                    // out of index
                    if(firstWordIndex + 1 >= curString.length()) {
                        firstLineIndex = curLineIndex + 1;
                        firstWordIndex = 0;
                    }
                    break LineLoop;
                }
                lineInfoList.add(0, curList.get(i));
            }
            for(LineInfo li : lineInfoList)
                Log.d("MewX", "full: " + li.text);

            // not full to continue, set curWord as last index of the string
            if(curLineIndex - 1 >= 0) {
                mLoader.setCurrentIndex(-- curLineIndex);
                curWordIndex = mLoader.getCurrentStringLength();
            }
            else {
                Log.d("MewX", "jump 2");
                firstLineIndex = 0;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList = new ArrayList<>();
                calcFromFirst();
                break;
            }
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
        String percentage = "( " + (lastLineIndex + 1) * 100 / mLoader.getElementCount() + "% )";
        final int textWidth = (int) widgetTextPaint.measureText(percentage);
        canvas.drawText(percentage, screenDrawArea.second.x - textWidth, screenDrawArea.second.y + widgetFontHeihgt, widgetTextPaint);
    }

    private void drawContent(Canvas canvas) {
        int heightSum = screenDrawArea.first.y + fontHeight; // The baseline (i.e. y).
        for(int i = 0; i < lineInfoList.size(); i ++) {
            final LineInfo li = lineInfoList.get(i);
            if( i != 0 ) {
                if(li.text.length() > 2 && li.text.substring(0, 2).equals("　　")) {
                    heightSum += pxParagraphDistance;
                }
                else {
                    heightSum += pxLineDistance;
                }
            }

            Log.d(WenkuReaderPageView.class.getSimpleName(), "draw: " + li.text);
            if(li.type == WenkuReaderLoader.ElementType.TEXT) {
                canvas.drawText( li.text, (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                heightSum += fontHeight;
            } else if(li.type == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT){
                if (bitmapInfoList == null) {
                    canvas.drawText("Unexpected array: " + li.text.substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
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
                    canvas.drawText("正在加载图片：" + li.text.substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
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
                        canvas.drawText("正在加载图片：" + li.text.substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
                    } else {
                        int new_x = (screenDrawArea.second.x - screenDrawArea.first.x - bi.width) / 2 + bi.x_beg;
                        int new_y = (screenDrawArea.second.y - screenDrawArea.first.y - bi.height) / 2 + bi.y_beg;
                        canvas.drawBitmap(bi.bm, new_x, new_y, new Paint());
                    }
                }
            } else {
                canvas.drawText("（！请先用旧引擎浏览）图片" + li.text.substring(21), (float) screenDrawArea.first.x, (float) heightSum, textPaint);
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
        return firstLineIndex;
    }

    public int getFirstWordIndex() {
        return firstWordIndex;
    }

    public int getLastLineIndex() {
        return lastLineIndex;
    }

    public int getLastWordIndex() {
        return lastWordIndex;
    }

    private class AsyncLoadImage extends AsyncTask<BitmapInfo, Integer, Wenku8Error.ErrorCode> {
        BitmapInfo bi_bak;

        @Override
        protected Wenku8Error.ErrorCode doInBackground(BitmapInfo... params) {
            bi_bak = params[0];

            String imgFileName = GlobalConfig.generateImageFileNameByURL(lineInfoList.get(params[0].idxLineInfo).text);
            if(GlobalConfig.getAvailableNovolContentImagePath(imgFileName) == null) {
                if(!GlobalConfig.saveNovelContentImage(lineInfoList.get(params[0].idxLineInfo).text))
                    return Wenku8Error.ErrorCode.NETWORK_ERROR;
                imgFileName = GlobalConfig.generateImageFileNameByURL(lineInfoList.get(params[0].idxLineInfo).text);
            }

            ImageSize targetSize = new ImageSize(params[0].width, params[0].height); // result Bitmap will be fit to this size
            params[0].bm = ImageLoader.getInstance().loadImageSync("file://" + GlobalConfig.getAvailableNovolContentImagePath(imgFileName), targetSize);
            int width = params[0].bm.getWidth(), height = params[0].bm.getHeight();
            if(params[0].height / (float)params[0].width > height / (float)width) {
                // fit width
                float percentage = (float)height / width;
                params[0].height = (int)(params[0].width * percentage);
            }
            else {
                // fit height
                float percentage = (float)width / height;
                params[0].width = (int)(params[0].height * percentage);
            }
            params[0].bm = Bitmap.createScaledBitmap(params[0].bm, params[0].width, params[0].height, true);
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED)
                WenkuReaderPageView.this.postInvalidate();
            else
                Toast.makeText(getContext(), errorCode.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public void watchImageDetailed(Activity activity) {
        if(bitmapInfoList == null || bitmapInfoList.size() == 0 || bitmapInfoList.get(0).bm == null) {
            Toast.makeText(getContext(), getResources().getString(R.string.reader_view_image_no_image), Toast.LENGTH_SHORT).show();
        }
        else {
            Intent intent = new Intent(activity, ViewImageDetailActivity.class);
            intent.putExtra("path", GlobalConfig.getAvailableNovolContentImagePath(GlobalConfig.generateImageFileNameByURL(lineInfoList.get(bitmapInfoList.get(0).idxLineInfo).text)));
            activity.startActivity(intent);
            activity.overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
        }
    }
}
