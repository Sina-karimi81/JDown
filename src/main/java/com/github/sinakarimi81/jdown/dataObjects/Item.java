package com.github.sinakarimi81.jdown.dataObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    private String name;
    private String type;
    private Status status;
    private Long size;
    private String savePath;
    private String downloadUrl;
    public Boolean isResumable;

}
