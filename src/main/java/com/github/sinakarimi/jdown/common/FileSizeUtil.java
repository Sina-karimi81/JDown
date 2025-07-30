package com.github.sinakarimi.jdown.common;

public class FileSizeUtil {

    public static String calculateSize(long size) {
        if (size < 1024) {
            return String.format("%d %s (%d Bytes)", size, getUnit(0), size);
        }

        // Calculate the digit group (0 for B, 1 for KB, 2 for MB, etc.)
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        double calc = size / Math.pow(1024, digitGroups);
        return String.format("%.2f %s (%d Bytes)", calc, getUnit(digitGroups), size);
    }

    private static String getUnit(int numberOfDivision) {
        switch (numberOfDivision) {
            case 0 -> {
                return "B";
            }
            case 1 -> {
                return "KB";
            }
            case 2 -> {
                return "MB";
            }
            case 3 -> {
                return "GB";
            }
            case 4 -> {
                return "TB";
            }
            default -> {
                return "not known";
            }
        }
    }

}
