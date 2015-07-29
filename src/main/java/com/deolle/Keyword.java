package com.deolle;

import java.util.ArrayList;

/**
 * Created by AlexAran on 27/07/2015.
 */
public class Keyword {
    private Integer chatId;
    private ArrayList<String> keywords;

    public Keyword() {
        super();
        keywords = new ArrayList<>();
    }

    public Integer getChatId() {
        return chatId;
    }

    public void setChatId(Integer chatId) {
        this.chatId = chatId;
    }

    public ArrayList<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(ArrayList<String> keywords) {
        this.keywords = keywords;
    }
}
