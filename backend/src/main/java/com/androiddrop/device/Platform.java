package com.androiddrop.device;

import java.util.Locale;

public enum Platform {

	MAC, ANDROID, IOS, WINDOWS, LINUX, OTHER;

	public static Platform fromUserAgent(String userAgent) {
		if (userAgent == null) {
			return OTHER;
		}
		// Order matters: Android UAs mention Linux, iOS UAs mention "Mac OS X".
		if (userAgent.contains("Android")) {
			return ANDROID;
		}
		if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
			return IOS;
		}
		if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
			return MAC;
		}
		if (userAgent.contains("Windows")) {
			return WINDOWS;
		}
		if (userAgent.contains("Linux") || userAgent.contains("CrOS")) {
			return LINUX;
		}
		return OTHER;
	}

	public static Platform ofThisMachine() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			return MAC;
		}
		if (os.contains("win")) {
			return WINDOWS;
		}
		if (os.contains("nux") || os.contains("nix")) {
			return LINUX;
		}
		return OTHER;
	}

}
