package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.download.DownloadTaskManager;
import lombok.Getter;

public class ClassManager {

    public static final String DB_NAME = "JDownDB";

    @Getter
    private static TasksDAO tasksDAO;
    @Getter
    private static DownloadTaskManager downloadTaskManager;

    /**
     * loads all the business logic classes, must be called otherwise everything is going to be null
     */
    public static void load() {
        tasksDAO = TasksDAO.getInstance(DB_NAME);
        downloadTaskManager = DownloadTaskManager.getInstance(tasksDAO);
    }

}
