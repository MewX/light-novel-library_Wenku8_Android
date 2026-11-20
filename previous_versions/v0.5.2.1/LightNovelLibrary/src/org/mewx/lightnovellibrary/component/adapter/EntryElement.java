/**
 *  Entry Element
 **
 *  Structure of entry element on LibraryFragment.
 **/

package org.mewx.lightnovellibrary.component.adapter;

public class EntryElement {
	private String code, name;
	// private int imageId;
	private String url;

	// public EntryElement( String name, int imageId ) {
	// this.name = name;
	// this.imageId = imageId;
	// return;
	// }

	public EntryElement(String code, String name, String url) {
		this.code = code;
		this.name = name;
		this.url = url;
		// this.imageId = 0;
		return;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}
	
	public String getCode( ) {
		return code;
	}
}
