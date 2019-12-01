package com.deolle.tldrbot.manager;

import com.deolle.tldrbot.persistence.model.Keyword;
import com.deolle.tldrbot.persistence.model.Setting;
import com.deolle.tldrbot.service.TelegramService;
import com.deolle.tldrbot.vo.MessageVo;
import com.deolle.tldrbot.vo.UpdateVo;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.dto.Response;
import org.telegram.dto.Update;
import com.deolle.tldrbot.persistence.repository.TldrBotRepository;
import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TldrBotManager {

  private static List<Keyword> keywords = new ArrayList<>();
  private static List<Setting> settings = new ArrayList<>();

  private static final Logger LOGGER = LoggerFactory.getLogger(TldrBotManager.class);

  private static final String INSERT_KEYWORD      = "INSERT INTO  KEYWORDS (CHAT_ID, KEYWORD) VALUES ( ? , ? );";
  private static final String DELETE_KEYWORD      = "DELETE FROM  KEYWORDS WHERE CHAT_ID = ? AND KEYWORD = ? ;";

  private static final String INSERT_CONFIG       = "REPLACE INTO CONFIG (CHAT_ID, VERBOSE, TTL) VALUES ( ? , ? , ? );";

  @Value("${tldr.verbose}")
  private boolean VERBOSE;

  @Value("${tldr.ttl}")
  private int TTL;

  private static Connection connection = TldrBotRepository.openDatabase();

  private static Integer lastUpdateId = 0;

  private static List<MessageVo> queueList = new ArrayList<>();

  private TelegramService telegramService;

  @Autowired
  public void setTelegramService(TelegramService telegramService) {
    this.telegramService = telegramService;
  }

  static {
    TldrBotManager.initializeTldrBot();
  }

  private static void initializeTldrBot() {
    TldrBotRepository tldrBotRepository = new TldrBotRepository();

    if (connection == null) {
      return;
    }
    keywords = tldrBotRepository.loadKeywordsData(connection);
    settings = tldrBotRepository.loadSettingsData(connection);
  }

  @Scheduled(fixedRate = 2000)
  public void manageNewMessages() {
    queueList = keepOnlyRecentMessagesOnQueue(queueList);

    List<UpdateVo> updates = getUpdates(lastUpdateId);

    handleNewMessages(updates);
  }

  private void handleNewMessages(List<UpdateVo> updates) {
    for (UpdateVo updateVo : updates) {
      MessageVo messageVo = updateVo.getMessageVo();

      if (messageVo.getText() != null) {
        if (messageVo.getText().startsWith("/")) {
          if (messageVo.getText().equalsIgnoreCase("/tldr")) {
            doCommandTLDR(queueList, messageVo);
          } else if (messageVo.getText().toLowerCase().startsWith("/add ")) {
            doCommandAdd(messageVo, connection);
          } else if (messageVo.getText().toLowerCase().startsWith("/remove ")) {
            doCommandRemove(messageVo, connection);
          } else if (messageVo.getText().equalsIgnoreCase("/list")) {
            doCommandList(messageVo);
          } else if (messageVo.getText().toLowerCase().startsWith("/ttl ")) {
            doCommandTTL(messageVo, connection);
          } else if (messageVo.getText().equalsIgnoreCase("/verbose")) {
            doCommandVerbose(messageVo, connection);
          }
        } else {
          String msgText = messageVo.getText().toLowerCase();
          for (Keyword keyword : keywords) {
            if (keyword.getChatId() == messageVo.getChat()) {
              queueList.addAll(
                  keyword.getKeywords()
                      .stream()
                      .filter(msgText::contains)
                      .map(kw -> messageVo)
                      .distinct()
                      .collect(Collectors.toList()));
            }
          }
        }
      }
      lastUpdateId = updateVo.getUpdateId() + 1;
    }
  }

  private List<UpdateVo> getUpdates(int updateId) {
    String sUpdates = "";
    try {
      sUpdates = telegramService.getUpdates("offset=" + updateId);
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

  private List<MessageVo> keepOnlyRecentMessagesOnQueue(List<MessageVo> queueList) {
    List<MessageVo> newQueueList = new ArrayList<>();

    for (MessageVo messageVo : queueList) {
      int ttl = settings.stream()
          .filter(setting -> setting.getChatId() == messageVo.getChat())
          .map(Setting::getiTTL)
          .max(Comparator.comparingInt(Integer::intValue))
          .orElse(TTL);

      LocalDateTime messageExpirationTime = LocalDateTime.now();
      messageExpirationTime.minusHours(ttl);
      long messageEpoch = messageExpirationTime.toEpochSecond(ZoneOffset.UTC);

      if (messageVo.isYoungerThanEpoch(messageEpoch)) {
        newQueueList.add(messageVo);
      }
    }

    return newQueueList;
  }

  private void doCommandTLDR(List<MessageVo> queueList, MessageVo messageVo) {
    try {
      Setting temp = null;
      for (Setting setting : settings) {
        if (setting.getChatId().equals(messageVo.getChat())) {
          temp = setting;
        }
      }

      int response = messageVo.getChat();
      if (temp != null && !temp.getVerbose()) {
        response = messageVo.getFrom();
      }
      String header = "Nothing to gossip about, how disgusting!";
      boolean found = false;
      for (MessageVo msgTemp : queueList) {
        if (messageVo.getChat() == msgTemp.getChat()) {
          found = true;
          break;
        }
      }
      if (found) {
        header = "Agh! So much gossip, how disgusting!";
      }
      try {
        String params = "chat_id=" + response + "&text=" + URLEncoder.encode(header, "UTF-8");
        telegramService.sendMessage(params);
      } catch (IOException e) {
        e.printStackTrace();
      }
      for (MessageVo oldMsg : queueList) {
        if (oldMsg.getChat() == messageVo.getChat()) {
          String params = "chat_id=" + response + "&from_chat_id=" + oldMsg.getChat() + "&message_id=" + oldMsg.getMessageId();
          telegramService.forwardMessage(params);
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void doCommandAdd(MessageVo messageVo, Connection connection) {
    boolean added = false;
    String sNewKeyword = "";
    if (messageVo.getText().length() > 5) {
      try {
        sNewKeyword = messageVo.getText().toLowerCase();
        sNewKeyword = trim(sNewKeyword.substring(5, Math.min(sNewKeyword.length(), 35)));
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
      }
    } else {
      return;
    }

    if (!sNewKeyword.trim().equals("")) {
      for (Keyword keyword : keywords) {
        if (keyword.getChatId() == messageVo.getChat()) {
          boolean duplicate = false;
          for (String tempKW : keyword.getKeywords()) {
            if (tempKW.equalsIgnoreCase(sNewKeyword)) {
              duplicate = true;
              break;
            }
          }
          if (!duplicate) {
            added = keyword.getKeywords().add(sNewKeyword);
          }
          break;
        }
      }
      if (!added) {
        try {
          Keyword newKeyword = new Keyword();
          newKeyword.setChatId(messageVo.getChat());
          newKeyword.getKeywords().add(sNewKeyword);
          added = keywords.add(newKeyword);
        } catch (StringIndexOutOfBoundsException e) {
          e.printStackTrace();
        }
      }

      if (added) {
        PreparedStatement pstmt = null;
        try {
          pstmt = connection.prepareStatement(INSERT_KEYWORD);
          pstmt.setInt(1, messageVo.getChat());
          pstmt.setString(2, sNewKeyword);
          pstmt.executeUpdate();
        } catch (SQLException e) {
          e.printStackTrace();
        } finally {
          if (pstmt != null) {
            try {
              pstmt.close();
            } catch (SQLException e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
  }

  private void doCommandRemove(MessageVo messageVo, Connection connection) {
    Setting temp = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(messageVo.getChat())) {
        temp = setting;
      }
    }

    int response = messageVo.getChat();
    if (temp != null && !temp.getVerbose()) {
      response = messageVo.getFrom();
    }

    boolean removed = false;
    String rKeyword = "";
    if (messageVo.getText().length() > 8) {
      try {
        rKeyword = messageVo.getText().toLowerCase();
        rKeyword = trim(rKeyword.substring(8, Math.min(rKeyword.length(), 38)));
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
      }
    } else {
      return;
    }

    if (!rKeyword.trim().equals("")) {
      for (Keyword keyword : keywords) {
        if (keyword.getChatId() == messageVo.getChat()) {
          removed = keyword.getKeywords().remove(rKeyword);

          if (keyword.getKeywords().isEmpty()) {
            try {
              String params = "chat_id=" + response + "&text=" + URLEncoder.encode("Keywords' list is empty.", "UTF-8");
              telegramService.sendMessage(params);
            } catch (IOException e) {
              e.printStackTrace();
              continue;
            }
          }

          break;
        }
      }

      if (removed) {
        PreparedStatement pstmt = null;
        try {
          pstmt = connection.prepareStatement(DELETE_KEYWORD);
          pstmt.setInt(1, messageVo.getChat());
          pstmt.setString(2, rKeyword);
          pstmt.executeUpdate();
        } catch (SQLException e) {
          e.printStackTrace();
        } finally {
          if (pstmt != null) {
            try {
              pstmt.close();
            } catch (SQLException e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
  }

  private void doCommandList(MessageVo messageVo) {
    Setting temp = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(messageVo.getChat())) {
        temp = setting;
        break;
      }
    }

    int response = messageVo.getChat();
    if (temp != null && !temp.getVerbose()) {
      response = messageVo.getFrom();
    }

    if (keywords.isEmpty()) {
      try {
        String params = "chat_id=" + response + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
        telegramService.sendMessage(params);
      } catch (IOException e) {
        e.printStackTrace();
        return;
      }
    } else {
      for (Keyword keyword : keywords) {
        if (keyword.getChatId() == messageVo.getChat()) {
          if (keyword.getKeywords().isEmpty()) {
            try {
              String params = "chat_id=" + response + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
              telegramService.sendMessage(params);
            } catch (IOException e) {
              e.printStackTrace();
              continue;
            }
          } else {
            String responseList = "";
            for (String kw : keyword.getKeywords()) {
              responseList += kw + "\n";
            }
            try {
              String params = "chat_id=" + response + "&text=" + URLEncoder.encode(responseList, "UTF-8");
              telegramService.sendMessage(params);
            } catch (IOException e) {
              e.printStackTrace();
              continue;
            }
          }
          break;
        }
      }
    }
  }

  private void doCommandTTL(MessageVo messageVo, Connection connection) {
    String time = "";
    if (messageVo.getText().length() > 5) {
      try {
        time = messageVo.getText().toLowerCase();
        time = trim(time.substring(5, Math.min(time.length(), 10)));
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
      }
    } else {
      return;
    }

    if (time.length() > 0) {
      try {
        int value = Integer.parseInt(time);
        Setting tempSetting = null;
        for (Setting setting : settings) {
          if (setting.getChatId().equals(messageVo.getChat())) {
            setting.setiTTL(value);
            tempSetting = setting;
            break;
          }
        }

        if (tempSetting == null) {
          tempSetting = new Setting();
          tempSetting.setChatId(messageVo.getChat());
          tempSetting.setVerbose(VERBOSE);
          tempSetting.setiTTL(value);
          settings.add(tempSetting);
        }

        PreparedStatement pstmt = null;
        try {
          pstmt = connection.prepareStatement(INSERT_CONFIG);
          pstmt.setInt(1, tempSetting.getChatId());
          pstmt.setBoolean(2, tempSetting.getVerbose());
          pstmt.setInt(3, tempSetting.getiTTL());
          pstmt.executeUpdate();
        } catch (SQLException e) {
          e.printStackTrace();
        } finally {
          if (pstmt != null) {
            try {
              pstmt.close();
            } catch (SQLException e) {
              e.printStackTrace();
            }
          }
        }
      } catch (NumberFormatException e) {
        e.printStackTrace();
      }
    }
  }

  private void doCommandVerbose(MessageVo messageVo, Connection connection) {
    Setting tempSetting = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(messageVo.getChat())) {
        setting.setVerbose(!setting.getVerbose());
        tempSetting = setting;
        break;
      }
    }

    if (tempSetting == null) {
      tempSetting = new Setting();
      tempSetting.setChatId(messageVo.getChat());
      tempSetting.setVerbose(!VERBOSE);
      tempSetting.setiTTL(TTL);
      settings.add(tempSetting);
    }

    int response = messageVo.getChat();
    String responseMode = "Verbose mode activated. Now I'll answer in public chat, how disgusting...";
    if (tempSetting != null && !tempSetting.getVerbose()) {
      //response = msg.getFrom().getId();
      responseMode = "Verbose mode disabled. Now I'll answer in private chat, how disgusting...";
    }

    try {
      String params = "chat_id=" + response + "&text=" + URLEncoder.encode(responseMode, "UTF-8");
      telegramService.sendMessage(params);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    PreparedStatement pstmt = null;
    try {
      pstmt = connection.prepareStatement(INSERT_CONFIG);
      pstmt.setInt(1, tempSetting.getChatId());
      pstmt.setBoolean(2, tempSetting.getVerbose());
      pstmt.setInt(3, tempSetting.getiTTL());
      pstmt.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      if (pstmt != null) {
        try {
          pstmt.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }
  }

  // Telegram sends multiple spaces as 32 160 32 160 so the usual trim won't work
  private String trim(String value) {
    int len = value.toCharArray().length;
    int st = 0;
    char[] val = value.toCharArray();    /* avoid getfield opcode */

    while ((st < len) && (val[st] <= ' ' || val[st] == (char) 160)) {
      st++;
    }
    while ((st < len) && (val[len - 1] <= ' ' || val[st] == (char) 160)) {
      len--;
    }
    return ((st > 0) || (len < value.toCharArray().length)) ? value.substring(st, len) : value;
  }
}
