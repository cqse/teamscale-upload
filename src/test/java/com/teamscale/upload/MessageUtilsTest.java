package com.teamscale.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageUtilsTest {

    /**
     * This test may fail locally if your PC has no hostname set.
     * It's purpose is to run in CI and verify that the hostname of the CI system can be extracted
     * on Windows and Linux.
     * <p>
     * If it fails on CI, please check if the CI system has a hostname.
     * If not, we need to set one in the CI instructions.
     */
    @Test
    void ensureHostnameCanBeRetrieved() {
        assertThat(MessageUtils.guessHostName()).startsWith("hostname: ");
    }

}