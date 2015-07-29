package com.deolle.telegram;

/**
 * Created by AlexAran on 26/07/2015.
 */
public class Response<T> {
    private Boolean ok;
    private T result;

    public Boolean getOk() {
        return ok;
    }

    public void setOk(Boolean ok) {
        this.ok = ok;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
