package com.github.sinakarimi.jdown.dataObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Range implements Comparable<Range> {

    private int from;
    private int to;

    public static Range valueOf(String range) {
        if (!range.contains("_")) {
            throw new IllegalArgumentException("The string is not a range representation!! input string: " + range);
        }

        String[] split = range.split("_");

        if (split.length != 2) {
            throw new IllegalArgumentException("The string is not a range representation!! input string: " + range);
        }

        int from = Integer.parseInt(split[0]);
        int to = Integer.parseInt(split[1]);
        return new Range(from, to);
    }

    public String rangeString() {
        return from + "_" + to;
    }

    @Override
    public int compareTo(Range o) {
        if (o == null || this.from > o.getFrom()) {
            return 1;
        } else if (this.from < o.getFrom()) {
            return -1;
        } else {
            return 0;
        }
    }
}
