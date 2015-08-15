/**
 *  Novel Icon
 **
 *  Just like NovelElement, but now it's just for test.
 **/

package org.mewx.lightnovellibrary.component.adapter;

public class NovelIcon {
	private int aid;
	private String name;
	private int num_hit, num_push, num_fav;

	private byte[] image;

	public NovelIcon(int aid, String name, int NH, int NP, int NF, byte[] img) {
		this.aid = aid;
		this.name = name;
		this.num_hit = NH;
		this.num_push = NP;
		this.num_fav = NF;
		this.image = img;
		return;
	}

	public String getName() {
		return name;
	}

	public int getAid() {
		return aid;
	}

	public int getNumHit() {
		return num_hit;
	}

	public int getNumPush() {
		return num_push;
	}

	public int getNumFav() {
		return num_fav;
	}

	public byte[] getImage() {
		return image;
	}
}
