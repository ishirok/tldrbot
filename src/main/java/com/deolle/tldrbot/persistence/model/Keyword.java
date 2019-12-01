package com.deolle.tldrbot.persistence.model;

import java.util.ArrayList;
import java.util.List;

public class Keyword {
  private Integer chatId;
  private List<String> keywords;

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

  public List<String> getKeywords() {
    return keywords;
  }
}
