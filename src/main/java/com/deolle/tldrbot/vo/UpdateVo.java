package com.deolle.tldrbot.vo;

import org.telegram.dto.Update;

public class UpdateVo {

  private int updateId;
  private MessageVo messageVo;

  public UpdateVo() {

  }

  public UpdateVo(Update update) {
    this.updateId = update.getUpdate_id();
    this.messageVo = new MessageVo(update.getMessage());
  }

  public int getUpdateId() {
    return updateId;
  }

  public void setUpdateId(int updateId) {
    this.updateId = updateId;
  }

  public MessageVo getMessageVo() {
    return messageVo;
  }

  public void setMessageVo(MessageVo messageVo) {
    this.messageVo = messageVo;
  }
}
