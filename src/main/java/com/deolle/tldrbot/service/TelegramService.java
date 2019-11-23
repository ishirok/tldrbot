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

  public String getMoreData(String method, String param) throws IOException {
    String sURL = TELEGRAM_API + BOT_KEY + "/";

    URL url = new URL(sURL + method);
    HttpsURLConnection uc = (HttpsURLConnection) url.openConnection();
    uc.setRequestMethod("POST");
    uc.setDoOutput(true);
    uc.setDoInput(true);
    uc.setUseCaches(false);
    uc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    uc.setReadTimeout(5 * 1000);
    uc.connect();

    DataOutputStream printout = new DataOutputStream(uc.getOutputStream());
    printout.write(param.getBytes());
    printout.flush();

    BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    StringBuilder stringBuilder = new StringBuilder();

    String line;
    while ((line = reader.readLine()) != null) {
      stringBuilder.append(line + "\n");
    }

    return stringBuilder.toString();
  }
}
