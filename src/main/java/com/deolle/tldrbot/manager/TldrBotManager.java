package com.deolle.tldrbot.manager;

import com.deolle.tldrbot.persistence.dto.Keyword;
import com.deolle.tldrbot.persistence.dto.Setting;
import com.deolle.tldrbot.service.TelegramService;
import org.telegram.dto.Message;
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

  private static ArrayList<Keyword> keywords      = new ArrayList<>();
  private static ArrayList<Setting> settings = new ArrayList<>();

  private static final Logger LOGGER = LoggerFactory.getLogger(TldrBotManager.class);

  private static final String INSERT_KEYWORD      = "INSERT INTO  KEYWORDS (CHAT_ID, KEYWORD) VALUES ( ? , ? );";
  private static final String DELETE_KEYWORD      = "DELETE FROM  KEYWORDS WHERE CHAT_ID = ? AND KEYWORD = ? ;";

  private static final String INSERT_CONFIG       = "REPLACE INTO CONFIG (CHAT_ID, VERBOSE, TTL) VALUES ( ? , ? , ? );";

  private static final boolean VERBOSE            = true;
  private static final int     TTL                = 8;

  private static Connection c = TldrBotRepository.openDatabase();

  private static Integer uid = 0;

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

    if (c == null) {
      return;
    }

    if (!tldrBotRepository.loadKeywordsData(c, keywords)) {
      return;
    }

    if (!tldrBotRepository.loadSettingsData(c, settings)) {
      return;
    }
  }

  @Scheduled(fixedRate = 2000)
  public void checkForNewMessages() {
    LOGGER.debug("Cron started!");
    ArrayList<Message> queueList = new ArrayList<>();

    for (int i = queueList.size() - 1; i >= 0; i--) {
      Calendar timeMargin = Calendar.getInstance();

      Setting temp = null;
      for (Setting setting : settings) {
        if (setting.getChatId().equals(queueList.get(i).getChat().getId())) {
          temp = setting;
        }
      }

      int ttl = TTL;
      if (temp != null) {
        ttl = temp.getiTTL();
      }
      timeMargin.add(Calendar.HOUR, -ttl);
      if (queueList.get(i).getDate() < timeMargin.getTime().getTime()/1000L) {
        queueList.remove(i);
      }
    }

    String sUpdates = "";
    try {
      sUpdates = telegramService.getUpdates("offset=" + uid);
    } catch (IOException e) {
      e.printStackTrace();
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ie) {
        ie.printStackTrace();
      }
    }

    Gson gs = new Gson();
    Type responseType = new TypeToken<Response<Update[]>>() {}.getType();
    Response<Update[]> updatesResponse = gs.fromJson(sUpdates, responseType);

    for (Update upd : updatesResponse.getResult()) {
      Message msg = upd.getMessage();

      if (msg.getText() != null) {
        if (msg.getText().startsWith("/")) {
          if (msg.getText().equalsIgnoreCase("/tldr")) {
            doCommandTLDR(queueList, msg);
          } else if (msg.getText().toLowerCase().startsWith("/add ")) {
            doCommandAdd(msg, c);
          } else if (msg.getText().toLowerCase().startsWith("/remove ")) {
            doCommandRemove(msg, c);
          } else if (msg.getText().equalsIgnoreCase("/list")) {
            doCommandList(msg);
          } else if (msg.getText().toLowerCase().startsWith("/ttl ")) {
            doCommandTTL(msg, c);
          } else if (msg.getText().equalsIgnoreCase("/verbose")) {
            doCommandVerbose(msg, c);
          }
        } else {
          String msgText = msg.getText().toLowerCase();
          for (Keyword keyword : keywords) {
            if (keyword.getChatId().intValue() == msg.getChat().getId().intValue()) {
              queueList.addAll(
                  keyword.getKeywords()
                      .stream()
                      .filter(msgText::contains)
                      .map(kw -> msg)
                      .distinct()
                      .collect(Collectors.toList()));
            }
          }
        }
      }
      uid = upd.getUpdate_id() + 1;
    }
  }

  private void doCommandTLDR(ArrayList<Message> queueList, Message msg) {
    try {
      Setting temp = null;
      for (Setting setting : settings) {
        if (setting.getChatId().equals(msg.getChat().getId())) {
          temp = setting;
        }
      }

      int response = msg.getChat().getId();
      if (temp != null && !temp.getVerbose()) {
        response = msg.getFrom().getId();
      }
      String header = "Nothing to gossip about, how disgusting!";
      boolean found = false;
      for (Message msgTemp : queueList) {
        if (msg.getChat().getId().equals(msgTemp.getChat().getId())) {
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
      for (Message oldMsg : queueList) {
        if (oldMsg.getChat().getId().equals(msg.getChat().getId())) {
          String params = "chat_id=" + response + "&from_chat_id=" + oldMsg.getChat().getId() + "&message_id=" + oldMsg.getMessage_id();
          telegramService.forwardMessage(params);
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void doCommandAdd(Message msg, Connection c) {
    boolean added = false;
    String sNewKeyword = "";
    if (msg.getText().length() > 5) {
      try {
        sNewKeyword = msg.getText().toLowerCase();
        sNewKeyword = trim(sNewKeyword.substring(5, Math.min(sNewKeyword.length(), 35)));
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
      }
    } else {
      return;
    }

    if (!sNewKeyword.trim().equals("")) {
      for (Keyword keyword : keywords) {
        if (keyword.getChatId().intValue() == msg.getChat().getId().intValue()) {
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
          newKeyword.setChatId(msg.getChat().getId());
          newKeyword.getKeywords().add(sNewKeyword);
          added = keywords.add(newKeyword);
        } catch (StringIndexOutOfBoundsException e) {
          e.printStackTrace();
        }
      }

      if (added) {
        PreparedStatement pstmt = null;
        try {
          pstmt = c.prepareStatement(INSERT_KEYWORD);
          pstmt.setInt(1, msg.getChat().getId());
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

  private void doCommandRemove(Message msg, Connection c) {
    Setting temp = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(msg.getChat().getId())) {
        temp = setting;
      }
    }

    int response = msg.getChat().getId();
    if (temp != null && !temp.getVerbose()) {
      response = msg.getFrom().getId();
    }

    boolean removed = false;
    String rKeyword = "";
    if (msg.getText().length() > 8) {
      try {
        rKeyword = msg.getText().toLowerCase();
        rKeyword = trim(rKeyword.substring(8, Math.min(rKeyword.length(), 38)));
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
      }
    } else {
      return;
    }

    if (!rKeyword.trim().equals("")) {
      for (Keyword keyword : keywords) {
        if (keyword.getChatId().intValue() == msg.getChat().getId().intValue()) {
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
          pstmt = c.prepareStatement(DELETE_KEYWORD);
          pstmt.setInt(1, msg.getChat().getId());
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

  private void doCommandList(Message msg) {
    Setting temp = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(msg.getChat().getId())) {
        temp = setting;
        break;
      }
    }

    int response = msg.getChat().getId();
    if (temp != null && !temp.getVerbose()) {
      response = msg.getFrom().getId();
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
        if (keyword.getChatId().intValue() == msg.getChat().getId().intValue()) {
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

  private void doCommandTTL(Message msg, Connection c) {
    String time = "";
    if (msg.getText().length() > 5) {
      try {
        time = msg.getText().toLowerCase();
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
          if (setting.getChatId().equals(msg.getChat().getId())) {
            setting.setiTTL(value);
            tempSetting = setting;
            break;
          }
        }

        if (tempSetting == null) {
          tempSetting = new Setting();
          tempSetting.setChatId(msg.getChat().getId());
          tempSetting.setVerbose(VERBOSE);
          tempSetting.setiTTL(value);
          settings.add(tempSetting);
        }

        PreparedStatement pstmt = null;
        try {
          pstmt = c.prepareStatement(INSERT_CONFIG);
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

  private void doCommandVerbose(Message msg, Connection c) {
    Setting tempSetting = null;
    for (Setting setting : settings) {
      if (setting.getChatId().equals(msg.getChat().getId())) {
        setting.setVerbose(!setting.getVerbose());
        tempSetting = setting;
        break;
      }
    }

    if (tempSetting == null) {
      tempSetting = new Setting();
      tempSetting.setChatId(msg.getChat().getId());
      tempSetting.setVerbose(!VERBOSE);
      tempSetting.setiTTL(TTL);
      settings.add(tempSetting);
    }

    int response = msg.getChat().getId();
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
      pstmt = c.prepareStatement(INSERT_CONFIG);
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
