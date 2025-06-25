package com.github.sinakarimi81.jdown.dataObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataSegment {

    private byte[] segment;
    private boolean isComplete;

    public DataSegment(int size) {
        segment = new byte[size];
        isComplete = false;
    }

}
