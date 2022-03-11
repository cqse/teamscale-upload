package com.teamscale.upload.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import okhttp3.HttpUrl;

class TeamscaleUrlUtilsTest {

	@Test
	void testEditUserUrl() {
		String url = TeamscaleUrlUtils.getEditUserUrl(HttpUrl.get("http://localhost:8080"), "build");
		assertEquals("http://localhost:8080/admin.html#users?action=edit&username=build", url);
	}

	@Test
	void testProjectPermissionUrl() {
		String url = TeamscaleUrlUtils.getProjectPermissionUrl(HttpUrl.get("http://localhost:8080"), "proj");
		assertEquals("http://localhost:8080/project.html#project?name=proj&action=roles", url);
	}

	@Test
	void testProjectPerspectiveUrl() {
		String url = TeamscaleUrlUtils.getProjectPerspectiveUrl(HttpUrl.get("http://localhost:8080"));
		assertEquals("http://localhost:8080/project.html", url);
	}

}