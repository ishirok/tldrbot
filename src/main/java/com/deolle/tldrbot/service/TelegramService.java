package com.deolle.tldrbot.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramService {

  @Value("${tldr.telegramApi}")
  private String TELEGRAM_API;

  @Value("${tldr.botKey}")
  private String BOT_KEY;

  public String sendMessage(String param) throws IOException {
    return invokeTelegramApi("sendMessage", param);
  }

  public String getUpdates(String param) throws IOException {
    return invokeTelegramApi("getUpdates", param);
  }

  public String forwardMessage(String param) throws IOException {
    return invokeTelegramApi("forwardMessage", param);
  }

  private String invokeTelegramApi(String method, String param) throws IOException {
    String textUrl = TELEGRAM_API + BOT_KEY + "/";
    URL url = new URL(textUrl + method);

    HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
    httpsURLConnection.setRequestMethod("POST");
    httpsURLConnection.setDoOutput(true);
    httpsURLConnection.setDoInput(true);
    httpsURLConnection.setUseCaches(false);
    httpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    httpsURLConnection.setReadTimeout(5 * 1000);
    httpsURLConnection.connect();

    DataOutputStream dataOutputStream = new DataOutputStream(httpsURLConnection.getOutputStream());
    dataOutputStream.write(param.getBytes());
    dataOutputStream.flush();

    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));

    StringBuilder stringBuilder = new StringBuilder();

    String line;
    while ((line = bufferedReader.readLine()) != null) {
      stringBuilder.append(line + "\n");
    }

    return stringBuilder.toString();
  }
}
