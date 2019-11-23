package com.deolle.tldrbot.dto;

import java.util.ArrayList;
import java.util.List;

public class KeywordDto {
  private Integer chatId;
  private List<String> keywords;

  public KeywordDto() {
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
