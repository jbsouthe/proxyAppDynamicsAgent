package com.cisco.josouthe;

import com.appdynamics.apm.appagent.api.DataScope;
import org.json.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;

public class HTTPRequestHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(HTTPRequestHandler.class);
    private JSONCommandParser jsonCommandParser;

    public HTTPRequestHandler(String path) {
        this.jsonCommandParser = new JSONCommandParser();
        logger.info("Initialized HTTP Request Handler for context: "+ path);
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        logger.debug("Handling Agent Proxy request from: "+ httpExchange.getRemoteAddress().toString());
        String inputString = "";
        String response = "0"; //place holder
        int responseCode = 200; //assume success

        if( "POST".equalsIgnoreCase(httpExchange.getRequestMethod()) ) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody(), "utf-8"));
                int b;
                StringBuilder sb = new StringBuilder(512);
                while ((b = reader.read()) != -1) sb.append((char) b);
                inputString = sb.toString();
                logger.debug("Processing Request: " + inputString);
                if (inputString.length() > 0) {
                    response = this.jsonCommandParser.parse(sb.toString());
                } else {
                    response = "Was expecting a JSON body with a command and other super helpful instructions to follow, please review docs";
                    responseCode = 400;
                }
            } catch (org.json.JSONException jsonEx ) {
                response = "Something is wrong with the JSON input: \""+ inputString +"\" The error is:"+ jsonEx;
                responseCode = 400;
            } catch (Exception e) {
                logger.warn("Error in Agent Proxy Processing: " + e.getMessage(), e);
                response = e.getMessage();
                responseCode = 500;
            }
        } else {
            response = "Bad Method " + httpExchange.getRequestMethod() + ", use POST instead";
            responseCode = 400;
        }

        try {
            httpExchange.sendResponseHeaders(responseCode, response.getBytes().length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            logger.info("Sending Response: "+ response);
            os.close();
        } catch( Exception e ) {
            logger.warn("Exception in sending response: "+ e,e);
        }
        logger.debug("Finished Proxy Agent handle request");
    }
}
