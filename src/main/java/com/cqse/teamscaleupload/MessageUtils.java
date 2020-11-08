package com.cqse.teamscaleupload;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;

public class MessageUtils {

    public static String createDefaultMessage(String commit, String partition, Collection<String> formats) {
        String revisionPart = "";
        if (commit != null) {
            revisionPart = "\nfor revision: " + commit;
        }

        String formatList = formats.stream().map(String::toUpperCase).collect(Collectors.joining(", "));

        return partition + " external analysis results uploaded at " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) +
                "\n\nuploaded from " + guessHostName() +
                revisionPart +
                "\nincludes data in the following formats: " + formatList;
    }

    /**
     * Package-visible for testing.
     */
    /*package*/
    static String guessHostName() {
        // we do not include the IP address here as one host may have
        // - multiple network interfaces
        // - each with multiple IP addresses
        // - in either IPv4 or IPv6 format
        // - and it is unclear which of those is "the right one" or even just which is useful (e.g. loopback or virtual
        // adapters are not useful and might even confuse readers)
        try {
            return "hostname: " + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "a computer without a hostname";
        }
    }

}
