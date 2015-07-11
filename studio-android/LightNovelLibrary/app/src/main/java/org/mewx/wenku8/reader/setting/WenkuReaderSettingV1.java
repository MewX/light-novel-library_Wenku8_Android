package org.mewx.wenku8.reader.setting;

/**
 * Created by MewX on 2015/7/8.
 *
 * This is the first version of reader activity setting.
 * New version extends from this setting class.
 */
public class WenkuReaderSettingV1 {
    /**
     * Setting values, containing default value;
     * Setting Class must be defined before reader activity created.
     */

    // enum type
    public enum PAGE_BACKGROUND_TYPE {
        SYSTEM_DEFAULT,
        SYSTEM_01,
        CUSTOM
    }

    // font setting
    private int fontSize = 18; // in "sp"
    private boolean useCustomFont = false; // Custom font must declare this first!
    private String customFontPath = "";

    // paragraph setting
    private int lineDistance = 32; // in "dp"
    private int paragraphDistance = 48; // in "dp"
    private int paragraghEdgeDistance = 32; // in "dp", text part edge distance (left&right)

    // page setting
    private int pageEdgeDistance = 8; // in "dp", top&right&bottom&left 4 directions distances to screen edge, and paragraph to side view widgets
    private PAGE_BACKGROUND_TYPE pageBackgroundType = PAGE_BACKGROUND_TYPE.SYSTEM_DEFAULT;
    private String pageBackgrounCustomPath = "";


    /**
     * Construct Function
     */
    public WenkuReaderSettingV1() {
        // TODO: update values
    }


    /**
     * gets & sets functions
     */

    public void setFontSize(int s) {
        fontSize = s;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setUseCustomFont(boolean b) {
        useCustomFont = b;
    }

    public boolean getUseCustomFont() {
        return useCustomFont;
    }

    public void setCustomFontPath(String s) {
        // should test file before set this value, allow setting, but not allow use!
        customFontPath = s;
    }

    public String getCustomFontPath() {
        return customFontPath;
    }

    public void setLineDistance(int l) {
        lineDistance = l;
    }

    public int getLineDistance() {
        return lineDistance;
    }

    public void setParagraphDistance(int l) {
        paragraphDistance = l;
    }

    public int getParagraphDistance() {
        return paragraphDistance;
    }

    public void setParagraphEdgeDistance(int l) {
        paragraghEdgeDistance = l;
    }

    public int getParagraghEdgeDistance() {
        return paragraghEdgeDistance;
    }

    public void setPageEdgeDistance(int l) {
        pageEdgeDistance = l;
    }

    public int getPageEdgeDistance() {
        return pageEdgeDistance;
    }

    public void setPageBackgroundType(PAGE_BACKGROUND_TYPE t) {
        pageBackgroundType = t;
    }

    public PAGE_BACKGROUND_TYPE getPageBackgroundType() {
        return pageBackgroundType;
    }

    public void setPageBackgrounCustomPath(String s) {
        pageBackgrounCustomPath = s;
    }

    public String getPageBackgrounCustomPath() {
        return pageBackgrounCustomPath;
    }
}
