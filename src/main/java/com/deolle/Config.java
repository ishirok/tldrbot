package com.deolle;

/**
 * Created by AlexAran on 02/08/2015.
 */
public class Config {

    private Integer chatId;
    private Boolean verbose;
    private Integer iTTL;


    public Integer getChatId() {
        return chatId;
    }

    public void setChatId(Integer chatId) {
        this.chatId = chatId;
    }

    public Boolean getVerbose() {
        return verbose;
    }

    public void setVerbose(Boolean verbose) {
        this.verbose = verbose;
    }

    public Integer getiTTL() {
        return iTTL;
    }

    public void setiTTL(Integer iTTL) {
        this.iTTL = iTTL;
    }
}
