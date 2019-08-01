package com.flock.plugin;

import hudson.model.BuildListener;
import net.sf.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RequestsManager {

    public static void sendNotification(String webhookUrl, JSONObject payload, BuildListener listener) throws IOException {
        URL url = new URL(webhookUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");

        // For POST only - START
        con.setDoOutput(true);

        OutputStream os = con.getOutputStream();
        os.write(payload.toString().getBytes());
        os.flush();
        os.close();
        // For POST only - END

        int responseCode = con.getResponseCode();
        listener.getLogger().println(FlockLoggerInformationProvider.FLOCK_LOGS_IDENTIFIER + "POST Response Code : " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            listener.getLogger().println(FlockLoggerInformationProvider.FLOCK_LOGS_IDENTIFIER + response.toString());
        } else {
            listener.getLogger().println(FlockLoggerInformationProvider.FLOCK_LOGS_IDENTIFIER + "POST request not worked. Got following response code - " + con.getResponseMessage());
        }
    }
}
