package com.cisco.josouthe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class SocketRequestHandlerThread extends Thread {
    private static final Logger logger = LogManager.getLogger(SocketRequestHandlerThread.class);
    private final Socket connection;
    private JSONCommandParser jsonCommandParser;

    public SocketRequestHandlerThread(Socket client ) {
        this.connection = client;
        this.jsonCommandParser = new JSONCommandParser();
    }

    public void run() {
        String inputString = "";
        String response = "0";
        try {
            BufferedReader reader = new BufferedReader( new InputStreamReader( connection.getInputStream() ));
            int b;
            StringBuilder sb = new StringBuilder(512);
            while(( b = reader.read()) != -1 ) sb.append((char) b);
            inputString = sb.toString();
            logger.debug("Processing Request: "+ inputString);
            if (inputString.length() > 0 ) {
                response = this.jsonCommandParser.parse(sb.toString());
            } else {
                response = "Was expecting a JSON body with a command and other super helpful instructions to follow, please review docs";
            }
        } catch (org.json.JSONException jsonEx ) {
            response = "Something is wrong with the JSON input: \""+ inputString +"\" The error is:"+ jsonEx;
        } catch (IOException e) {
            logger.warn("Error in Agent Proxy Processing: "+ e.getMessage(),e );
        }

        try {
            OutputStream os = connection.getOutputStream();
            os.write(response.getBytes());
            logger.info("Sending Response: "+ response);
            os.close();
        } catch (IOException e) {
            logger.warn("Exception in sending response: "+ e.getMessage(),e );
        }

    }
}
