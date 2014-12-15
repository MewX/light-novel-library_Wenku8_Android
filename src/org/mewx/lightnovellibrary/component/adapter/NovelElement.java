/**
 *  Novel Element
 **
 *  Structure of novel element on NovelListActivity.
 **/

package org.mewx.lightnovellibrary.component.adapter;

public class NovelElement {
	private int aid;
	private String name;
	private String writer;
	private String press;
	private int num_hit, num_push, num_fav;
	int bookLen;

	private String imgUrl;

	public NovelElement(int aid, String name, String writer, String press,
			int bookLen, String imgUrl) {
		this.aid = aid;
		this.name = name;
		this.writer = writer;
		this.press = press;
		this.bookLen = bookLen;
		this.imgUrl = imgUrl;
		return;
	}

	// temp use
	public NovelElement(int aid, String name, int NH, int NP, int NF,
			String imgUrl) {
		this.aid = aid;
		this.name = name;
		this.num_hit = NH;
		this.num_push = NP;
		this.num_fav = NF;
		this.imgUrl = imgUrl;
		return;
	}

	public String getName() {
		return name;
	}

	public int getAid() {
		return aid;
	}

	public String getWriter() {
		return writer;
	}

	public String getPress() {
		return press;
	}

	public int getBookLen() {
		return bookLen;
	}

	// temp use
	public int getNumHit() {
		return num_hit;
	}

	public int getNumPush() {
		return num_push;
	}

	public int getNumFav() {
		return num_fav;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
		return;
	}

}
