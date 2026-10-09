package com.famag.streamplayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class XtreamApi {
    private final String host;
    private final String username;
    private final String password;

    public XtreamApi(String host, String username, String password) {
        this.host = host.trim().replaceAll("/+$", "");
        this.username = username;
        this.password = password;
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private String request(String action) throws Exception {
        String separator = host.contains("?") ? "&" : "?";
        String address = host + "/player_api.php" + separator
                + "username=" + encode(username)
                + "&password=" + encode(password);
        if (action != null && !action.isEmpty()) {
            address += "&action=" + encode(action);
        }

        HttpURLConnection connection = (HttpURLConnection)
                new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");

        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                throw new Exception("Il server non ha restituito una risposta.");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            if (status < 200 || status >= 300) {
                throw new Exception("Risposta HTTP: " + status);
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    public JSONObject login() throws Exception {
        JSONObject response = new JSONObject(request(""));
        JSONObject info = response.optJSONObject("user_info");
        if (info == null || info.optInt("auth", 0) != 1) {
            throw new Exception("Accesso non riuscito. Controlla server e credenziali.");
        }
        return response;
    }

    public JSONArray getLiveCategories() throws Exception {
        return new JSONArray(request("get_live_categories"));
    }

    public JSONArray getLiveStreams() throws Exception {
        return new JSONArray(request("get_live_streams"));
    }

    public JSONArray getVodCategories() throws Exception {
        return new JSONArray(request("get_vod_categories"));
    }

    public JSONArray getVodStreams() throws Exception {
        return new JSONArray(request("get_vod_streams"));
    }

    public JSONArray getSeriesCategories() throws Exception {
        return new JSONArray(request("get_series_categories"));
    }

    public JSONArray getSeries() throws Exception {
        return new JSONArray(request("get_series"));
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String buildStreamUrl(String type, String id, String extension) {
        String safeType = type.equals("live") ? "live" : type;
        String suffix = extension == null || extension.isEmpty() ? "mp4" : extension;
        try {
            return host + "/" + safeType + "/" + encode(username) + "/"
                    + encode(password) + "/" + encode(id) + "." + suffix;
        } catch (Exception e) {
            return "";
        }
    }
}
