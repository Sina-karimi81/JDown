package com.github.sinakarimi81.jdown.dataObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemInfo {

    private String name;
    private String type;
    private Status status;
    private Long size;
    private String savePath;
    private String downloadUrl;
    private Boolean resumable;

}
