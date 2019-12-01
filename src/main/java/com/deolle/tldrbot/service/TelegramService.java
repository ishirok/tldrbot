package com.deolle.tldrbot.service;

import com.deolle.tldrbot.vo.UpdateVo;
import java.io.IOException;
import java.util.List;

public interface TelegramService {

  String sendMessage(String parameters) throws IOException;

  List<UpdateVo> getUpdates(int lastUpdateId);

  String forwardMessage(String parameters) throws IOException;
}
