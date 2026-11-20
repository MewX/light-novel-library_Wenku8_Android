package org.mewx.wenku8.reader.view;

import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import java.util.ArrayList;
import java.util.List;

class WenkuReaderPaginator {
    private final WenkuReaderLoader mLoader;
    private final TextMeasurer mTextMeasurer;
    private final int textAreaWidth;
    private final int textAreaHeight;
    private final int fontHeight;
    private final int pxLineDistance;
    private final int pxParagraphDistance;

    private int firstLineIndex;
    private int firstWordIndex;
    private int lastLineIndex;
    private int lastWordIndex;
    private List<LineInfo> lineInfoList;

    WenkuReaderPaginator(WenkuReaderLoader loader, TextMeasurer textMeasurer,
                                int textAreaWidth, int textAreaHeight, int fontHeight,
                                int pxLineDistance, int pxParagraphDistance) {
        this.mLoader = loader;
        this.mTextMeasurer = textMeasurer;
        this.textAreaWidth = textAreaWidth;
        this.textAreaHeight = textAreaHeight;
        this.fontHeight = fontHeight;
        this.pxLineDistance = pxLineDistance;
        this.pxParagraphDistance = pxParagraphDistance;
        this.lineInfoList = new ArrayList<>();
    }

    void setPageStart(int lineIndex, int wordIndex) {
        this.firstLineIndex = lineIndex;
        this.firstWordIndex = wordIndex;
        mLoader.setCurrentIndex(firstLineIndex);
    }

    void setPageEnd(int lineIndex, int wordIndex) {
        this.lastLineIndex = lineIndex;
        this.lastWordIndex = wordIndex;
        mLoader.setCurrentIndex(lastLineIndex);
    }

    /**
     * Calc page from first to last.
     * firstLineIndex & firstWordIndex set.
     */
    void calcFromFirst() {
        int widthSum = 0;
        int heightSum = fontHeight;
        StringBuilder tempText = new StringBuilder();
        lineInfoList.clear();

        // System.out.println("firstLineIndex = " + firstLineIndex + "; firstWordIndex = " + firstWordIndex);
        for(int curLineIndex = firstLineIndex, curWordIndex = firstWordIndex; curLineIndex < mLoader.getElementCount(); ) {
            // init paragraph head vars
            if(curWordIndex == 0 && mLoader.getCurrentType() == WenkuReaderLoader.ElementType.TEXT) {
                // leading space
                widthSum = 2 * fontHeight;
                tempText = new StringBuilder("　　");
            }
            else if(mLoader.getCurrentType() == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                if(!lineInfoList.isEmpty()) {
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
                lineInfoList.add(new LineInfo(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, mLoader.getCurrentAsString()));
                break;
            }

            // get a record of line
            if(mLoader.getCurrentAsString() == null || mLoader.getCurrentStringLength() == 0) {
                // System.out.println("empty string! in " + curLineIndex + "(" + curWordIndex + ")");
                curWordIndex = 0;
                if(curLineIndex >= mLoader.getElementCount()) {
                    // out of bounds
                    break;
                }
                mLoader.setCurrentIndex(++ curLineIndex);
                continue;
            }
            String temp = mLoader.getCurrentAsString().charAt(curWordIndex) + "";
            int tempWidth = (int) mTextMeasurer.measureText(temp);

            // Line full?
            if(widthSum + tempWidth > textAreaWidth) {
                // wrap line, save line
                lineInfoList.add(new LineInfo(WenkuReaderLoader.ElementType.TEXT, tempText.toString()));
                heightSum += pxLineDistance;

                // change vars for next line
                if(heightSum + fontHeight > textAreaHeight) {
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
                lineInfoList.add(new LineInfo(WenkuReaderLoader.ElementType.TEXT, tempText.toString()));
                heightSum += pxParagraphDistance;

                // height not acceptable
                if(heightSum + fontHeight > textAreaHeight) {
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
    void calcFromLast() {

        int heightSum = 0;
        boolean isFirst = true;
        mLoader.setCurrentIndex(lastLineIndex);
        lineInfoList.clear();

        LineLoop:
        for(int curLineIndex = lastLineIndex, curWordIndex = lastWordIndex; curLineIndex >= 0; ) {
            // calc curLine to curWord(contained), make a String list
            WenkuReaderLoader.ElementType curType = mLoader.getCurrentType();
            String curString = mLoader.getCurrentAsString();

            // Skip null or empty strings (same guard as calcFromFirst).
            if (curString == null || curString.isEmpty()) {
                if (curLineIndex - 1 >= 0) {
                    mLoader.setCurrentIndex(-- curLineIndex);
                    curWordIndex = mLoader.getCurrentStringLength();
                } else {
                    firstLineIndex = 0;
                    firstWordIndex = 0;
                    mLoader.setCurrentIndex(firstLineIndex);
                    lineInfoList.clear();
                    calcFromFirst();
                    break;
                }
                continue;
            }

            // special to image
            if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT && !lineInfoList.isEmpty()) {
                // System.out.println("jump 1");
                firstLineIndex = curLineIndex + 1;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList.clear();
                calcFromFirst();
                break;
            }
            else if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                // one image on page
                lastLineIndex = firstLineIndex = mLoader.getCurrentIndex();
                firstWordIndex = 0;
                lastWordIndex = mLoader.getCurrentStringLength() - 1;
                lineInfoList.add(new LineInfo( WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, mLoader.getCurrentAsString()));
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
                int width = (int) mTextMeasurer.measureText(c);
                if(tempWidth + width > textAreaWidth) {
                    // save line to next
                    curList.add(new LineInfo(WenkuReaderLoader.ElementType.TEXT, temp));

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
                    curList.add(new LineInfo(WenkuReaderLoader.ElementType.TEXT, temp));
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
                if(heightSum > textAreaHeight) {
                    // calc first index
                    int indexCount = -2;
                    for(int j = 0; j <= i; j ++) indexCount += curList.get(j).text().length();
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
            // for(LineInfo li : lineInfoList)
            //     System.out.println("full: " + li.text);

            // not full to continue, set curWord as last index of the string
            if(curLineIndex - 1 >= 0) {
                mLoader.setCurrentIndex(-- curLineIndex);
                curWordIndex = mLoader.getCurrentStringLength();
            }
            else {
                // System.out.println("jump 2");
                firstLineIndex = 0;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList.clear();
                calcFromFirst();
                break;
            }
        }
    }

    List<LineInfo> getLineInfoList() {
        return lineInfoList;
    }

    int getFirstLineIndex() { return firstLineIndex; }
    int getFirstWordIndex() { return firstWordIndex; }
    int getLastLineIndex() { return lastLineIndex; }
    int getLastWordIndex() { return lastWordIndex; }
}
