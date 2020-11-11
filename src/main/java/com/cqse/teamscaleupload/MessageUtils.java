package com.cqse.teamscaleupload;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
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

        return partition + " external analysis results uploaded at " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) +
                "\n\nuploaded from " + guessHostName() +
                "\nfor revision: " + revisionOrBranchTimestamp +
                "\nincludes data in the following formats: " + formatList;
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
    static String guessHostName() {
        try {
            return "hostname: " + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "a computer without a hostname";
        }
    }

}
