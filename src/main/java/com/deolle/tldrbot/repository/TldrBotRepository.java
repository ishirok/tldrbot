package com.deolle.tldrbot.repository;

import com.deolle.tldrbot.dto.KeywordDto;
import com.deolle.tldrbot.dto.SettingsDto;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    Connection c = null;
    Statement stmt = null;
    try {
      Class.forName("org.sqlite.JDBC");
      c = DriverManager.getConnection("jdbc:sqlite:tldr.db");
      c.setAutoCommit(true);

      stmt = c.createStatement();
      stmt.executeUpdate(CREATE_TABLE_CFG);
      stmt.executeUpdate(CREATE_INDEX_CFG);
      stmt.executeUpdate(CREATE_TABLE_KW);
      stmt.executeUpdate(CREATE_INDEX_KW);
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
        stmt.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    return c;
  }

  public boolean loadKeywordsData(Connection c, List<KeywordDto> keywords) {
    LOGGER.debug("*** Initiating load of keywords from database ***");
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = c.createStatement();
      rs = stmt.executeQuery(SELECT_KEYWORDS);
      LOGGER.debug("Reviewing keywords...");
      while (rs.next()) {
        int chatId = rs.getInt(1);
        String kw = rs.getString(2);
        LOGGER.debug("Found chat id "+chatId+" and keyword "+kw);

        KeywordDto keyword = null;
        for (KeywordDto tempKW : keywords) {
          if (tempKW.getChatId().equals(chatId)) {
            keyword = tempKW;
            break;
          }
        }
        if (keyword != null) {
          keyword.getKeywords().add(kw);
          LOGGER.debug("Added keyword "+kw+" to chat id "+chatId);
        } else {
          keyword = new KeywordDto();
          keyword.setChatId(chatId);
          keyword.getKeywords().add(kw);
          keywords.add(keyword);
          LOGGER.debug("Added new chat id "+chatId+" and keyword "+kw);
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    return true;
  }

  public boolean loadConfigData(Connection c, List<SettingsDto> configs) {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = c.createStatement();
      rs = stmt.executeQuery(SELECT_CONFIG);
      while (rs.next()) {
        int chatId = rs.getInt(1);
        boolean verb = rs.getBoolean(2);
        int ttl = rs.getInt(3);

        SettingsDto config = new SettingsDto();
        config.setChatId(chatId);
        config.setVerbose(verb);
        config.setiTTL(ttl);
        configs.add(config);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    return true;
  }
}
