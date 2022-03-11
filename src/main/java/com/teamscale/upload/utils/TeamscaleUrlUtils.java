package com.teamscale.upload.utils;

import okhttp3.HttpUrl;

/**
 * Utils to construct Teamscale URLs to be displayed to the user, e.g. in error
 * messages. This handles some Teamscale quirks. E.g. Teamscale always expects
 * the fragment ("#user" etc.) to appear before the query parameters.
 */
public class TeamscaleUrlUtils {

	/** Returns a URL to the edit page of the given user. */
	public static String getEditUserUrl(HttpUrl teamscaleBaseUrl, String username) {
		return fixFragment(teamscaleBaseUrl.newBuilder().addPathSegment("admin.html#users")
				.addQueryParameter("action", "edit").addQueryParameter("username", username));
	}

	/** Returns a URL to the edit page of the given user. */
	public static String getProjectPermissionUrl(HttpUrl teamscaleBaseUrl, String project) {
		return fixFragment(teamscaleBaseUrl.newBuilder().addPathSegment("project.html#project")
				.addQueryParameter("name", project).addQueryParameter("action", "roles"));
	}

	/**
	 * Teamscale requires that the fragment be present before the query parameters.
	 * OkHttp always encodes the fragment after the query parameters. So we have to
	 * encode the fragment in the path, which unfortunately escapes the "#"
	 * separator. This function undoes this unwanted encoding.
	 */
	private static String fixFragment(HttpUrl.Builder url) {
		return url.toString().replaceFirst("%23", "#");
	}

	/** Returns a URL to the project perspective. */
	public static String getProjectPerspectiveUrl(HttpUrl teamscaleBaseUrl) {
		return teamscaleBaseUrl.newBuilder().addPathSegments("project.html").toString();
	}

}
