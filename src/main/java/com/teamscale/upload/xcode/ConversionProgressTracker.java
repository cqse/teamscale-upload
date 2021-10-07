package com.teamscale.upload.xcode;

import com.teamscale.upload.utils.LogUtils;

/**
 * Utility class used to report conversion progress.
 */
class ConversionProgressTracker {

    private final long startTimeMilliseconds;

    private final int total;

    private int processed = 0;

    private int lastPercent = 0;

    ConversionProgressTracker(long startTimeMilliseconds, int total) {
        this.startTimeMilliseconds = startTimeMilliseconds;
        this.total = total;
    }

    /**
     * Reports the conversion of one file as completed.
     */
    void reportProgress() {
        processed++;

        int percent = processed * 100 / total;
        long currentTimeMillis = System.currentTimeMillis();

        if (lastPercent < percent) {
            LogUtils.info(String.format("Converted %d percent of files at %f conversions per hour (%d/%d)", percent,
                    getConversionsPerHour(currentTimeMillis), processed, total));
            lastPercent = percent;
        }

        if (total <= processed) {
            LogUtils.info(String.format("Conversion finished after %d seconds.",
                    (currentTimeMillis - startTimeMilliseconds) / 1000));
        }
    }

    private double getConversionsPerHour(long currentTimeMillis) {
        return (60.0 * 60 * 1000 / (currentTimeMillis - startTimeMilliseconds)) * processed;
    }
}
