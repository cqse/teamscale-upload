package com.teamscale.upload.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utilities for the upload session's message parameter.
 */
public class MessageUtils {

    /**
     * Creates the default message based on the given revision, branch and timestamp, partition and
     * uploaded formats.
     */
    public static String createDefaultMessage(String revisionOrBranchTimestamp, String partition, Collection<String> formats) {
        String formatList = formats.stream().map(String::toUpperCase).collect(Collectors.joining(", "));

        return MessageFormat.format("{0} external analysis results uploaded at {1}" +
                        "\n\nuploaded from {2}\nfor revision: {3}" +
                        "\nincludes data in the following formats: {4}",
                partition, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()),
                formatHostName(), revisionOrBranchTimestamp, formatList);
    }

    /**
     * We do not include the IP address in the message as one host may have
     * - multiple network interfaces
     * - each with multiple IP addresses
     * - in either IPv4 or IPv6 format
     * - and it is unclear which of those is "the right one" or even just which is useful (e.g. loopback or virtual
     * adapters are not useful and might even confuse readers)
     * <p>
     * Package-visible for testing.
     */
    /*package*/
    static String formatHostName() {
        return guessHostName().map(hostname -> "hostname: " + hostname)
                .orElse("a computer without a hostname");
    }

    private static Optional<String> guessHostName() {
        try {
            return Optional.ofNullable(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

}
