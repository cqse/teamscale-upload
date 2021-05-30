package com.cqse.teamscaleupload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TODO
 */
class TestArgumentHolder {
    private String url = TestConstants.url;
    private String user = TestConstants.user;
    private String accessKey = getAccessKeyFromCi();
    private String project = TestConstants.project;
    private final String format = TestConstants.format;
    private final String partition = TestConstants.partition;
    private String pattern = TestConstants.pattern;
    private String input = null;

    public TestArgumentHolder withPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public TestArgumentHolder withUrl(String url) {
        this.url = url;
        return this;
    }

    public TestArgumentHolder withAccessKey(String accessKey) {
        this.accessKey = accessKey;
        return this;
    }

    public TestArgumentHolder withUser(String user) {
        this.user = user;
        return this;
    }

    public TestArgumentHolder withProject(String project) {
        this.project = project;
        return this;
    }

    public TestArgumentHolder withInput(String input) {
        this.input = input;
        return this;
    }

    public String[] toStringArray() {
        List<String> arguments = new ArrayList<>(Arrays.asList("-s", url, "-u", user, "-a", accessKey, "-f", format,
                "-p", project, "-t", partition));
        if (input != null) {
            arguments.add("-i");
            arguments.add(input);
        }
        arguments.add(pattern);

        return arguments.toArray(new String[arguments.size()]);
    }

    private static String getAccessKeyFromCi() {
        String accessKey = System.getenv("ACCESS_KEY");
        if (accessKey == null) {
            return TestConstants.accessKey;
        }
        return accessKey;
    }
}
