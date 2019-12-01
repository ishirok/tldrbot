package org.telegram.service;

import com.deolle.tldrbot.vo.UpdateVo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.dto.Response;
import org.telegram.dto.Update;

public class TelegramService implements com.deolle.tldrbot.service.TelegramService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TelegramService.class);

  @Value("${tldr.telegramApi}")
  private String TELEGRAM_API;

  @Value("${tldr.botKey}")
  private String BOT_KEY;

  public String sendMessage(String parameters) throws IOException {
    return invokeTelegramApiTo("sendMessage", parameters);
  }

  public List<UpdateVo> getUpdates(int lastUpdateId) {
    String sUpdates = "";
    try {
      sUpdates = invokeTelegramApiTo("getUpdates", "offset=" + lastUpdateId);
    } catch (IOException e) {
      LOGGER.warn("Failed to receive new messages from Telegram, will retry soon...");
    }

    Gson gs = new Gson();
    Type responseType = new TypeToken<Response<Update[]>>() {}.getType();
    Response<Update[]> updatesResponse = gs.fromJson(sUpdates, responseType);

    return Arrays.stream(updatesResponse.getResult())
        .map(UpdateVo::new)
        .collect(Collectors.toList());
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
