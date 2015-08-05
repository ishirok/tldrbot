package com.deolle;

import com.deolle.telegram.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static String TELEGRAMAP                = "https://api.telegram.org/bot";
    private static ArrayList<Keyword> keywords      = new ArrayList<>();
    private static ArrayList<Config> configs        = new ArrayList<>();
    private static String botKey                    = "";

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
    private static final String INSERT_KEYWORD      = "INSERT INTO  KEYWORDS (CHAT_ID, KEYWORD) VALUES ( ? , ? );";
    private static final String DELETE_KEYWORD      = "DELETE FROM  KEYWORDS WHERE CHAT_ID = ? AND KEYWORD = ? ;";

    private static final String SELECT_CONFIG       = "SELECT CHAT_ID, VERBOSE, TTL FROM CONFIG;";
    private static final String INSERT_CONFIG       = "REPLACE INTO CONFIG (CHAT_ID, VERBOSE, TTL) VALUES ( ? , ? , ? );";

    private static final boolean VERBOSE            = true;
    private static final int     TTL                = 8;

    public static void main(String[] args) {
        int uid = 0;
        ArrayList<Message> queueList = new ArrayList<>();

        ResourceBundle rb = ResourceBundle.getBundle("tldr");
        botKey = rb.getString("botkey");

        Connection c = openDatabase();

        if (c == null) {
            return;
        }

        if (!loadKeywordsData(c)) {
            return;
        }

        if (!loadConfigData(c)) {
            return;
        }

        while (true) {
            String sUpdates = "";
            try {
                sUpdates = getMoreData("getUpdates", "offset=" + uid);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                continue;
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
                        for (Keyword kws : keywords) {
                            if (kws.getChatId().equals(msg.getChat().getId())) {
                                queueList.addAll(kws.getKeywords().stream().filter(kw -> msgText.contains(kw)).map(kw -> msg).distinct().collect(Collectors.toList()));
                            }
                        }
                    }
                }
                uid = upd.getUpdate_id() + 1;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int i = queueList.size() - 1; i >= 0; i--) {
                Calendar timeMargin = Calendar.getInstance();

                Config temp = null;
                for (Config config : configs) {
                    if (config.getChatId().equals(queueList.get(i).getChat().getId())) {
                        temp = config;
                    }
                }

                int ttl = TTL;
                if (temp != null) {
                    ttl = temp.getiTTL();
                }
                timeMargin.add(Calendar.HOUR, -ttl);
                if (queueList.get(i).getDate().before(timeMargin.getTime())) {
                    queueList.remove(i);
                }
            }
        }

        /*try {
            c.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/
    }

    private static Connection openDatabase() {
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
                return null;
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

    private static boolean loadKeywordsData(Connection c) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = c.createStatement();
            rs = stmt.executeQuery(SELECT_KEYWORDS);
            while ( rs.next() ) {
                int chatId = rs.getInt(1);
                String kw = rs.getString(2);

                Keyword keyword = null;
                for (Keyword tempKW : keywords) {
                    if (tempKW.getChatId().equals(chatId)) {
                        keyword = tempKW;
                        break;
                    }
                }
                if (keyword != null) {
                    keyword.getKeywords().add(kw);
                } else {
                    keyword = new Keyword();
                    keyword.setChatId(chatId);
                    keyword.getKeywords().add(kw);
                    keywords.add(keyword);
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

    private static boolean loadConfigData(Connection c) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = c.createStatement();
            rs = stmt.executeQuery(SELECT_CONFIG);
            while ( rs.next() ) {
                int chatId = rs.getInt(1);
                boolean verb = rs.getBoolean(2);
                int ttl = rs.getInt(3);

                Config config = new Config();
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

    private static String getMoreData(String method, String param) throws IOException {
        String sURL = TELEGRAMAP + botKey + "/";

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

    private static void doCommandTLDR(ArrayList<Message> queueList, Message msg) {
        try {
            Config temp = null;
            for (Config config : configs) {
                if (config.getChatId().equals(msg.getChat().getId())) {
                    temp = config;
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
                getMoreData("sendMessage", params);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (Message oldMsg : queueList) {
                if (oldMsg.getChat().getId().equals(msg.getChat().getId())) {
                    String params = "chat_id=" + response + "&from_chat_id=" + oldMsg.getChat().getId() + "&message_id=" + oldMsg.getMessage_id();
                    getMoreData("forwardMessage", params);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void doCommandAdd(Message msg, Connection c) {
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
            for (Keyword kws : keywords) {
                if (kws.getChatId().equals(msg.getChat().getId())) {
                    boolean duplicate = false;
                    for (String tempKW : kws.getKeywords()) {
                        if (tempKW.equalsIgnoreCase(sNewKeyword)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        added = kws.getKeywords().add(sNewKeyword);
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

    private static void doCommandRemove(Message msg, Connection c) {
        Config temp = null;
        for (Config config : configs) {
            if (config.getChatId().equals(msg.getChat().getId())) {
                temp = config;
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
            for (Keyword kws : keywords) {
                if (kws.getChatId().equals(msg.getChat().getId())) {
                    removed = kws.getKeywords().remove(rKeyword);

                    if (kws.getKeywords().isEmpty()) {
                        try {
                            String params = "chat_id=" + response + "&text=" + URLEncoder.encode("Keywords' list is empty.", "UTF-8");
                            getMoreData("sendMessage", params);
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

    private static void doCommandList(Message msg) {
        Config temp = null;
        for (Config config : configs) {
            if (config.getChatId().equals(msg.getChat().getId())) {
                temp = config;
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
                getMoreData("sendMessage", params);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            for (Keyword kws : keywords) {
                if (kws.getChatId().equals(msg.getChat().getId())) {
                    if (kws.getKeywords().isEmpty()) {
                        try {
                            String params = "chat_id=" + response + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
                            getMoreData("sendMessage", params);
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                    } else {
                        String responseList = "";
                        for (String kw : kws.getKeywords()) {
                            responseList += kw + "\n";
                        }
                        try {
                            String params = "chat_id=" + response + "&text=" + URLEncoder.encode(responseList, "UTF-8");
                            getMoreData("sendMessage", params);
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

    private static void doCommandTTL(Message msg, Connection c) {
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
                Config temp = null;
                for (Config config : configs) {
                    if (config.getChatId().equals(msg.getChat().getId())) {
                        config.setiTTL(value);
                        temp = config;
                        break;
                    }
                }

                if (temp == null) {
                    temp = new Config();
                    temp.setChatId(msg.getChat().getId());
                    temp.setVerbose(VERBOSE);
                    temp.setiTTL(value);
                    configs.add(temp);
                }

                PreparedStatement pstmt = null;
                try {
                    pstmt = c.prepareStatement(INSERT_CONFIG);
                    pstmt.setInt(1, temp.getChatId());
                    pstmt.setBoolean(2, temp.getVerbose());
                    pstmt.setInt(3, temp.getiTTL());
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

    private static void doCommandVerbose(Message msg, Connection c) {
        Config temp = null;
        for (Config config : configs) {
            if (config.getChatId().equals(msg.getChat().getId())) {
                config.setVerbose(!config.getVerbose());
                temp = config;
                break;
            }
        }

        if (temp == null) {
            temp = new Config();
            temp.setChatId(msg.getChat().getId());
            temp.setVerbose(!VERBOSE);
            temp.setiTTL(TTL);
            configs.add(temp);
        }

        PreparedStatement pstmt = null;
        try {
            pstmt = c.prepareStatement(INSERT_CONFIG);
            pstmt.setInt(1, temp.getChatId());
            pstmt.setBoolean(2, temp.getVerbose());
            pstmt.setInt(3, temp.getiTTL());
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
    private static String trim(String value) {
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
