package com.deolle.telegram;

/**
 * Created by AlexAran on 26/07/2015.
 */
public class Update {

    private Integer update_id;
    private Message message;

    public Integer getUpdate_id() {
        return update_id;
    }

    public void setUpdate_id(Integer update_id) {
        this.update_id = update_id;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
