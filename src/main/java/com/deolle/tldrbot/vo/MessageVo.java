package com.deolle.tldrbot.vo;

import org.telegram.dto.Message;

public class MessageVo {

  private int messageId;
  private int from;
  private int date;
  private int chat;
  private String text;

  public MessageVo() {

  }

  public MessageVo(Message message) {
    this.messageId = message.getMessage_id();
    this.from = message.getFrom().getId();
    this.date = message.getDate();
    this.chat = message.getChat().getId();
    this.text = message.getText();
  }

  public int getMessageId() {
    return messageId;
  }

  public void setMessageId(int messageId) {
    this.messageId = messageId;
  }

  public int getFrom() {
    return from;
  }

  public void setFrom(int from) {
    this.from = from;
  }

  public int getDate() {
    return date;
  }

  public void setDate(int date) {
    this.date = date;
  }

  public int getChat() {
    return chat;
  }

  public void setChat(int chat) {
    this.chat = chat;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isYoungerThanEpoch(long epoch) {
    return this.date >= epoch;
  }
}
