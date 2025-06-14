package com.github.sinakarimi81.jdown.dataObjects;

import com.github.sinakarimi81.jdown.download.DownloadTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    private ItemInfo itemInfo;
    private DownloadTask downloadTask;

}
