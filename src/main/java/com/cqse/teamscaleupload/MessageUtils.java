package com.cqse.teamscaleupload;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MessageUtils {

    public static String createDefaultMessage(String revision, String partition) {
        String revisionPart = "";
        if (revision != null) {
            revisionPart = "\nfor revision: " + revision;
        }

        return partition + " coverage uploaded at " +
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\n\n" +
                "uploaded from " + guessHostName() +
                revisionPart;
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
