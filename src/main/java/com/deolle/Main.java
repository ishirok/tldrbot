package com.deolle;

import com.deolle.telegram.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class Main {

    private static String TELEGRAMAP                = "https://api.telegram.org/bot";
    private static ArrayList<Keyword> keywords      = new ArrayList<>();
    private static String botKey                    = "";

    private static final String CREATE_TABLE        = "CREATE TABLE KEYWORDS " +
                                                        "(CHAT_ID   INT         NOT NULL, " +
                                                        "KEYWORD    TEXT        NOT NULL, " +
                                                        "CONSTRAINT KW_PK       PRIMARY KEY " +
                                                        "(CHAT_ID, KEYWORD) ON  CONFLICT IGNORE, " +
                                                        "CONSTRAINT KW_UQ       UNIQUE " +
                                                        "(CHAT_ID, KEYWORD) ON  CONFLICT IGNORE);";

    private static final String CREATE_INDEX        = "CREATE INDEX KW_IDX  ON  KEYWORDS (CHAT_ID, KEYWORD);";
    private static final String SELECT_KEYWORDS     = "SELECT CHAT_ID, KEYWORD FROM KEYWORDS;";
    private static final String INSERT_KEYWORD      = "INSERT INTO  KEYWORDS (CHAT_ID, KEYWORD) VALUES ( ? , ? );";
    private static final String DELETE_KEYWORD      = "DELETE FROM KEYWORDS WHERE CHAT_ID = ? AND KEYWORD = ? ;";

    public static void main(String[] args) {
        int uid = 0;
        ArrayList<Message> queueList = new ArrayList<>();

        ResourceBundle rb = ResourceBundle.getBundle("tldr");
        botKey = rb.getString("botkey");

        Connection c = openDatabase();

        if (c == null) {
            return;
        }

        if (!loadDataFromDatabase(c)) {
            return;
        }

        while (true) {
            String sUpdates = "";
            try {
                sUpdates = getMoreData("getUpdates", "offset=" + uid);
            } catch (IOException e) {
                e.printStackTrace();
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
                        }/* else if (msg.getText().toLowerCase().startsWith("/ttl ")) {
                            doCommandTTL(msg);
                        } else if (msg.getText().toLowerCase().startsWith("/verbose")) {
                            doCommandVerbose(msg);
                        }*/
                    } else {
                        String msgText = msg.getText().toLowerCase();
                        for (Keyword kws : keywords) {
                            if (kws.getChatId().equals(msg.getChat().getId())) {
                                queueList.addAll(kws.getKeywords().stream().filter(kw -> msgText.contains(kw)).map(kw -> msg).collect(Collectors.toList()));
                                break;
                            }
                        }
                    }
                }
                uid = upd.getUpdate_id() + 1;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (Message msg : queueList) {
                Calendar timeMargin = Calendar.getInstance();
                timeMargin.add(Calendar.HOUR, -8);
                if (msg.getDate().before(timeMargin.getTime())) {
                    queueList.remove(msg);
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
            stmt.executeUpdate(CREATE_TABLE);
            stmt.executeUpdate(CREATE_INDEX);
        } catch (SQLException e) {
            if (!e.getMessage().equals("table KEYWORDS already exists")) {
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

    private static boolean loadDataFromDatabase(Connection c) {
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
            for (Message oldMsg : queueList) {
                if (oldMsg.getChat().getId().equals(msg.getChat().getId())) {
                    String params = "chat_id=" + msg.getChat().getId() + "&from_chat_id=" + oldMsg.getChat().getId() + "&message_id=" + oldMsg.getMessage_id();
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
                            String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
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
        if (keywords.isEmpty()) {
            try {
                String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
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
                            String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
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
                            String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode(responseList, "UTF-8");
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

    private static void doCommandTTL(Message msg) {

    }

    private static void doCommandVerbose(Message msg) {

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
