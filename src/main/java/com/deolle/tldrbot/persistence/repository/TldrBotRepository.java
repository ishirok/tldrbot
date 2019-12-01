package com.deolle.tldrbot.persistence.repository;

import com.deolle.tldrbot.persistence.dto.Keyword;
import com.deolle.tldrbot.persistence.dto.Setting;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TldrBotRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(TldrBotRepository.class);

  private static final String CREATE_TABLE_KW     = "CREATE TABLE KEYWORDS " +
      "(CHAT_ID   INT         NOT NULL, " +
      "KEYWORD    TEXT        NOT NULL, " +
      "CONSTRAINT KW_PK       PRIMARY KEY " +
      "(CHAT_ID, KEYWORD) ON  CONFLICT IGNORE, " +
      "CONSTRAINT KW_UQ       UNIQUE " +
      "(CHAT_ID, KEYWORD) ON  CONFLICT IGNORE);";
  private static final String CREATE_TABLE_CFG    = "CREATE TABLE CONFIG " +
      "(CHAT_ID   INT         PRIMARY KEY ON CONFLICT REPLACE " +
      "UNIQUE ON CONFLICT REPLACE " +
      "NOT NULL, " +
      "VERBOSE    BOOLEAN, " +
      "TTL        INT);";

  private static final String CREATE_INDEX_KW     = "CREATE INDEX KW_IDX  ON  KEYWORDS    (CHAT_ID, KEYWORD);";
  private static final String CREATE_INDEX_CFG    = "CREATE INDEX CFG_IDX ON  CONFIG      (CHAT_ID);";

  private static final String SELECT_KEYWORDS     = "SELECT CHAT_ID, KEYWORD FROM KEYWORDS;";

  private static final String SELECT_CONFIG       = "SELECT CHAT_ID, VERBOSE, TTL FROM CONFIG;";

  public static Connection openDatabase() {
    Connection connection = null;
    Statement statement = null;
    try {
      Class.forName("org.sqlite.JDBC");
      connection = DriverManager.getConnection("jdbc:sqlite:tldr.db");
      connection.setAutoCommit(true);

      statement = connection.createStatement();
      statement.executeUpdate(CREATE_TABLE_CFG);
      statement.executeUpdate(CREATE_INDEX_CFG);
      statement.executeUpdate(CREATE_TABLE_KW);
      statement.executeUpdate(CREATE_INDEX_KW);
    } catch (SQLException e) {
      if (!e.getMessage().equals("table KEYWORDS already exists") &&
          !e.getMessage().equals("table CONFIG already exists")) {
        e.printStackTrace();
      }
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    } finally {
      try {
        statement.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    return connection;
  }

  public List<Keyword> loadKeywordsData(Connection connection) {
    LOGGER.debug("*** Initiating load of keywords from database ***");
    List<Keyword> keywords = new ArrayList<>();
    Statement statement = null;
    ResultSet resultSet = null;
    try {
      statement = connection.createStatement();
      resultSet = statement.executeQuery(SELECT_KEYWORDS);
      LOGGER.debug("Reviewing keywords...");
      while (resultSet.next()) {
        int chatId = resultSet.getInt(1);
        String stringKeyword = resultSet.getString(2);
        LOGGER.debug("Found chat id "+chatId+" and keyword "+stringKeyword);

        Keyword keyword = null;
        for (Keyword tempKeyword : keywords) {
          if (tempKeyword.getChatId() == chatId) {
            keyword = tempKeyword;
            break;
          }
        }
        if (keyword != null) {
          keyword.getKeywords().add(stringKeyword);
          LOGGER.debug("Added keyword "+stringKeyword+" to chat id "+chatId);
        } else {
          keyword = new Keyword();
          keyword.setChatId(chatId);
          keyword.getKeywords().add(stringKeyword);
          keywords.add(keyword);
          LOGGER.debug("Added new chat id "+chatId+" and keyword "+stringKeyword);
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    } finally {
      if (resultSet != null) {
        try {
          resultSet.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (statement != null) {
        try {
          statement.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    return keywords;
  }

  public List<Setting> loadSettingsData(Connection connection) {
    List<Setting> settings = new ArrayList<>();
    Statement statement = null;
    ResultSet resultSet = null;
    try {
      statement = connection.createStatement();
      resultSet = statement.executeQuery(SELECT_CONFIG);
      while (resultSet.next()) {
        int chatId = resultSet.getInt(1);
        boolean verb = resultSet.getBoolean(2);
        int ttl = resultSet.getInt(3);

        Setting setting = new Setting();
        setting.setChatId(chatId);
        setting.setVerbose(verb);
        setting.setiTTL(ttl);
        settings.add(setting);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    } finally {
      if (resultSet != null) {
        try {
          resultSet.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (statement != null) {
        try {
          statement.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    return settings;
  }
}
