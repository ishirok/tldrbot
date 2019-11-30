package org.telegram.service;

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

  public String sendMessage(String parameters) throws IOException {
    return invokeTelegramApiTo("sendMessage", parameters);
  }

  public String getUpdates(String parameters) throws IOException {
    return invokeTelegramApiTo("getUpdates", parameters);
  }

  public String forwardMessage(String parameters) throws IOException {
    return invokeTelegramApiTo("forwardMessage", parameters);
  }

  private String invokeTelegramApiTo(String method, String parameters) throws IOException {
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
    dataOutputStream.write(parameters.getBytes());
    dataOutputStream.flush();

    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));

    StringBuilder stringBuilder = new StringBuilder();

    String responseLine;
    while ((responseLine = bufferedReader.readLine()) != null) {
      stringBuilder.append(responseLine);
      stringBuilder.append("\n");
    }

    return stringBuilder.toString();
  }
}
