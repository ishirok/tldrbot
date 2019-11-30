package com.deolle.tldrbot.service;

import java.io.IOException;

public interface TelegramService {

  String sendMessage(String parameters) throws IOException;

  String getUpdates(String parameters) throws IOException;

  String forwardMessage(String parameters) throws IOException;
}
