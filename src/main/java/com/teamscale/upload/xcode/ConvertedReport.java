package com.teamscale.upload.xcode;

import java.io.File;

/**
 * A converted report.
 */
public class ConvertedReport {

    /**
     * The report format of the converted report file.
     */
    public final String reportFormat;

    /**
     * The converted report file.
     */
    public final File report;

    public ConvertedReport(String reportFormat, File report) {
        this.reportFormat = reportFormat;
        this.report = report;
    }
}
