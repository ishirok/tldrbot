package com.deolle;

import com.deolle.telegram.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.ResourceBundle;

public class Main {

    private static String TELEGRAMAP = "https://api.telegram.org/bot";
    private static ArrayList<Keyword> keywords = new ArrayList<>();
    private static String botKey = "";

    public static void main(String[] args) {
        int uid = 0;
        ArrayList<Message> queueList = new ArrayList<>();

        ResourceBundle rb = ResourceBundle.getBundle("tldr");
        botKey = rb.getString("botkey");

        while (true) {
            String sUpdates = "";
            try {
                String params = "offset=" + uid;
                sUpdates = getMoreData("getUpdates", params);
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
                        if (msg.getText().toLowerCase().startsWith("/tldr")) {
                            try {
                                for (Message oldMsg : queueList) {
                                    if (oldMsg.getChat().getId().equals(msg.getChat().getId())) {
                                        String params = "chat_id=" + msg.getChat().getId() + "&from_chat_id=" + oldMsg.getChat().getId() + "&message_id=" + oldMsg.getMessage_id();
                                        getMoreData("forwardMessage", params);
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                continue;
                            }
                        } else if (msg.getText().toLowerCase().startsWith("/add")) {
                            boolean added = false;
                            for (Keyword kws : keywords) {
                                if (kws.getChatId().equals(msg.getChat().getId())) {
                                    String sNewKeyword = msg.getText().toLowerCase().substring(5);
                                    if (!sNewKeyword.equals("")) {
                                        added = kws.getKeywords().add(sNewKeyword);
                                    }
                                }
                            }
                            if (!added) {
                                Keyword newKeyword = new Keyword();
                                String sNewKeyword = msg.getText().toLowerCase().substring(5);
                                newKeyword.setChatId(msg.getChat().getId());
                                newKeyword.getKeywords().add(sNewKeyword);
                                keywords.add(newKeyword);
                            }
                        } else if (msg.getText().toLowerCase().startsWith("/remove")) {
                            for (Keyword kws : keywords) {
                                if (kws.getChatId().equals(msg.getChat().getId())) {
                                    String newKeyword = msg.getText().toLowerCase().substring(8);
                                    if (!newKeyword.equals("")) {
                                        kws.getKeywords().remove(newKeyword);
                                    }
                                }
                                if (kws.getKeywords().isEmpty()) {
                                    try {
                                        String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode("Keywords list is empty.", "UTF-8");
                                        getMoreData("sendMessage", params);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        continue;
                                    }
                                }
                            }
                        } else if (msg.getText().toLowerCase().startsWith("/list")) {
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
                                        for (String kw : kws.getKeywords()) {
                                            try {
                                                String params = "chat_id=" + msg.getChat().getId() + "&text=" + URLEncoder.encode(kw, "UTF-8");
                                                getMoreData("sendMessage", params);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                continue;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    String msgText = msg.getText().toLowerCase();
                    boolean added = false;
                    for (Keyword kws : keywords) {
                        if (kws.getChatId().equals(msg.getChat().getId())) {
                            for (String kw : kws.getKeywords()) {
                                if (msgText.contains(kw)) {
                                    if (!added) {
                                        added = queueList.add(msg);
                                    }
                                    if (!msgText.contains("#" + kw)) {
                                        msgText = msgText.replace(kw, "#" + kw);
                                    }
                                }
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
}
