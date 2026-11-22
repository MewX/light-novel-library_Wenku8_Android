/**
 *  Novel Element
 **
 *  Structure of novel element on NovelListActivity.
 **/

package org.mewx.lightnovellibrary.component.adapter;

public class NovelElementSearch {
	private int aid;
	private String title;
	private String author;
	private int status; // 0 - not; 1 - finished
	private String update;
	private String intro;

	private String imgUrl;

	public NovelElementSearch(int aid, String title, String author, int status,
			String update, String intro, String imgUrl) {
		this.aid = aid;
		this.title = title;
		this.author = author;
		this.status = status;
		this.update = update;
		this.intro = intro;
		this.imgUrl = imgUrl;
		return;
	}

	public String getAuthor() {
		return author;
	}

	public int getAid() {
		return aid;
	}

	public String getTitle() {
		return title;
	}

	public int getStatus() {
		return status;
	}

	public String getUpdate() {
		return update;
	}

	public String getIntro() {
		return intro;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
		return;
	}

}
