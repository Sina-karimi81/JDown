package com.github.sinakarimi.jdown.dataObjects;

public enum Status {

    PAUSED("Paused"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELED("Canceled"),
    ERROR("Error");

    private String value;

    Status(String val) {
        value = val;
    }

    public String getValue() {
        return value;
    }

}
