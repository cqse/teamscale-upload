package com.cqse.teamscaleupload;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class InputManagerTest {

    /**
     * TODO
     */
    @Test
    public void testArgumentParsing() {
        InputManager inputManager = InputManager.parseArguments(new TestArgumentHolder().toStringArray());

        assertThat(inputManager).hasFieldOrPropertyWithValue("project", TestConstants.project);
        assertThat(inputManager).hasFieldOrPropertyWithValue("url", HttpUrl.parse(TestConstants.url));
        assertThat(inputManager).hasFieldOrPropertyWithValue("username", TestConstants.user);
        assertThat(inputManager).hasFieldOrPropertyWithValue("accessKey", TestConstants.accessKey);
        assertThat(inputManager).hasFieldOrPropertyWithValue("format", TestConstants.format.toUpperCase());
        assertThat(inputManager).hasFieldOrPropertyWithValue("partition", TestConstants.partition);
        assertThat(inputManager).hasFieldOrPropertyWithValue("pattern", TestConstants.pattern);
    }
}
