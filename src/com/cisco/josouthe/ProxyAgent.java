package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.Transaction;

import com.appdynamics.apm.appagent.api.DataScope;
import com.sun.net.httpserver.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

import javax.net.ssl.*;
import java.net.*;

import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.security.KeyStore;
import java.util.*;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ProxyAgent {
    private static final Logger logger = LogManager.getLogger(ProxyAgent.class);
    private static ConcurrentHashMap<String,Object> exitCallStash = null; //beware static objects growing too large
    private static ConcurrentHashMap<String,Transaction> transactionMap = null; //beware static objects growing too large


    public ProxyAgent(String configFileName) {
        LoggerContext loggerContext = Configurator.initialize("ProxyAgent", configFileName);
        exitCallStash = new ConcurrentHashMap<>();
        transactionMap = new ConcurrentHashMap<>();
        logger.info("System starting with configuration: " + configFileName );

        Properties props = new Properties();
        File configFile = new File(configFileName);
        InputStream is = null;
        if( configFile.canRead() ) {
            try {
                is = new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                System.err.println("Config file not found! Exception: "+e);
            }
        } else {
            URL configFileURL = getClass().getClassLoader().getResource(configFileName);
            logger.info("Config file URL: " + configFileURL.toExternalForm());
            is = getClass().getClassLoader().getResourceAsStream(configFileName);
        }
        try {
            props.load(is);
        } catch (IOException e) {
            logger.error("Error loading configuration: "+ configFileName +" Exception: "+ e.getMessage());
            return;
        }
        if( props.getProperty("appenders","nothing").equals("nothing") ) {
            Configurator.shutdown(loggerContext);
            Configurator.initialize("Failsafe", "main/resources/default-log4j2.properties");
            logger.info("System starting with configuration: " + configFileName );
            logger.info("No logging configuration defined, using default log4j2 config in main/resources/default-log4j2.properties, if this isn't wanted behavior, update config");
        }
        boolean allowRemote = false;
        if( props.getProperty("allowRemoteConnections","false").equalsIgnoreCase("true") )
            allowRemote=true;
        String proxyProtocol = props.getProperty("protocol", "http");
        int port = Integer.parseInt(props.getProperty("serverPort", "9999"));
        logger.info("Configuring Proxy Listener for Protocol: "+ proxyProtocol);
        switch (proxyProtocol) {
            case "logfiles": {
                WatchService watcher;
                try {
                    watcher = FileSystems.getDefault().newWatchService();
                } catch (IOException e) {
                    logger.fatal("IO Exception while getting a new file watcher service, exiting: "+ e.getMessage());
                    return;
                }
                ArrayList<LogDirectoryFollower> logFollowers = new ArrayList<>();
                boolean weStillHaveEntries = true;
                int count=0;
                while( weStillHaveEntries ) {
                    if( props.getProperty("logfiles."+count+".filenames", "not-set").equals("not-set") ) {
                        weStillHaveEntries = false;
                    } else {
                        try {
                            logFollowers.add(new LogDirectoryFollower(count, props, watcher));
                        } catch (ConfigurationException configurationException) {
                            logger.warn("Error setting up log file watcher, in configuration: " + configurationException.getMessage());
                        }
                        count++;
                    }
                }
                break;
            }
            case "tcp-socket": {
                try {
                    ServerSocket serverSocket = null;

                    if( allowRemote ) {
                        logger.info("Property allowRemoteConnections=true so listening on all interfaces");
                        serverSocket = new ServerSocket(port);
                    } else {
                        logger.info( "Property allowRemoteConnections=false(default) so only listening on loopback interface");
                        serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress() );
                    }
                    logger.info("Successfully opened server listener on port: "+ port);
                    while (true) {
                        try {
                            Socket socket = serverSocket.accept();
                            logger.debug("Accepted connection from " + socket.getRemoteSocketAddress());
                            SocketRequestHandlerThread socketRequestHandlerThread = new SocketRequestHandlerThread(socket);
                            socketRequestHandlerThread.start();
                        } catch (IOException ioe) {
                            logger.error("Connection handler exception: " + ioe, ioe);
                        }
                    }
                } catch (IOException e) {
                    logger.fatal("Exception in Server Socket Handler: " + e, e);
                }
                break;
            }
            case "http": {
                boolean useSSL = false;
                if( props.getProperty("useSSL","false").equalsIgnoreCase("true") )
                    useSSL=true;

                boolean useClientAuth = false;
                if( props.getProperty("authRequired","false").equalsIgnoreCase("true") )
                    useClientAuth=true;

                try{
                    InetSocketAddress address = null;
                    if( allowRemote ) {
                        logger.info("Property allowRemoteConnections=true so listening on all interfaces");
                        address = new InetSocketAddress( port );
                    } else {
                        logger.info( "Property allowRemoteConnections=false(default) so only listening on loopback interface");
                        address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
                    }
                    if( useSSL ) {
                        logger.info("Starting HTTPS Server...");
                        // initialise the HTTPS server
                        HttpsServer httpsServer = HttpsServer.create(address, 0);
                        SSLContext sslContext = SSLContext.getInstance(props.getProperty("sslContext", "TLS"));

                        // initialise the keystore
                        char[] password = props.getProperty("keystorePassword", "password").toCharArray();
                        KeyStore ks = KeyStore.getInstance(props.getProperty("keystoreInstance", "JKS"));
                        //keytool -genkeypair -keyalg RSA -alias selfsigned -keystore testkey.jks -storepass password -validity 360 -keysize 2048
                        FileInputStream fis = new FileInputStream(props.getProperty("keystoreFile", "testkey.jks"));
                        ks.load(fis, password);

                        // setup the key manager factory
                        KeyManagerFactory kmf = KeyManagerFactory.getInstance(props.getProperty("keyManagerFactory", "SunX509"));
                        kmf.init(ks, password);

                        // setup the trust manager factory
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(props.getProperty("trustManagerFactory", "SunX509"));
                        tmf.init(ks);

                        // setup the HTTPS context and parameters
                        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                        boolean finalUseClientAuth = useClientAuth;
                        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                            public void configure(HttpsParameters params) {
                                try {
                                    // initialise the SSL context
                                    SSLContext context = getSSLContext();
                                    SSLEngine engine = context.createSSLEngine();
                                    params.setNeedClientAuth(finalUseClientAuth);
                                    params.setCipherSuites(engine.getEnabledCipherSuites());
                                    params.setProtocols(engine.getEnabledProtocols());

                                    // Set the SSL parameters
                                    SSLParameters sslParameters = context.getSupportedSSLParameters();
                                    params.setSSLParameters(sslParameters);

                                } catch (Exception ex) {
                                    logger.error("Failed to create HTTPS port",ex);
                                }
                            }
                        });
                        HttpContext context = httpsServer.createContext(props.getProperty("proxy-uri","/agent"), new HTTPRequestHandler(props.getProperty("proxy-uri","/agent")));
                        if( useClientAuth )
                            context.setAuthenticator(new BasicAuthenticator(props.getProperty("proxy-uri","/agent")) {
                                                         @Override
                                                         public boolean checkCredentials(String user, String pass) {
                                                             return user.equals( props.getProperty("authUser")) && pass.equals(props.getProperty("authPassword"));
                                                         }
                                                     }
                            );
                        httpsServer.setExecutor(new ThreadPoolExecutor(4, Integer.parseInt(props.getProperty("threadPoolMaxSize", "8")), Integer.parseInt(props.getProperty("threadPoolKeepAliveSeconds", "30")), TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(Integer.parseInt(props.getProperty("threadPoolCapacity", "100")))));
                        httpsServer.start();
                        logger.info("Server Started");
                    } else {
                        logger.info("Starting HTTP Server (without SSL) ...");
                        HttpServer httpServer = HttpServer.create( address, 0);
                        HttpContext context = httpServer.createContext(props.getProperty("proxy-uri","/agent"), new HTTPRequestHandler(props.getProperty("proxy-uri","/agent")));
                        if( useClientAuth )
                            context.setAuthenticator(new BasicAuthenticator(props.getProperty("proxy-uri","/agent")) {
                                                         @Override
                                                         public boolean checkCredentials(String user, String pass) {
                                                             return user.equals( props.getProperty("authUser")) && pass.equals(props.getProperty("authPassword"));
                                                         }
                                                     }
                            );
                        httpServer.setExecutor(new ThreadPoolExecutor(4, Integer.parseInt(props.getProperty("threadPoolMaxSize", "8")), Integer.parseInt(props.getProperty("threadPoolKeepAliveSeconds", "30")), TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(Integer.parseInt(props.getProperty("threadPoolCapacity", "100")))));
                        httpServer.start();
                        logger.info("Server Started");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                break;
            }
            default: { logger.warn("Unknown protocol, please configure for either http or tcp-socket and restart"); break; }
        }
    }

    public static String startBusinessTransaction( String btName, String correlationHeaderOptional, String btType ){
        logger.debug("Begin startBusinessTransaction btName: "+ btName +" btType: "+ btType+ " Optional Correlation ID: "+ correlationHeaderOptional);
        String btUniqueId = null;
        if( btType != null && ! btType.equals( EntryTypes.POJO ) ) {
            logger.debug("Unknown BT Type: "+ btType +" for btName: "+ btName +" Setting Type instead to Custom");
            btType = null;
        }
        if( btName == null || "".equals(btName) ) {
            logger.warn("BT Name is a required parameter, not starting BT!");
            return "";
        }
        Transaction transaction = AppdynamicsAgent.startTransaction( btName, correlationHeaderOptional, btType, false );
        btUniqueId = transaction.getUniqueIdentifier();
        transactionMap.put(btUniqueId, transaction);
        logger.debug("Finish startBusinessTransaction Started BT btName: "+ btName +" btType: "+ (btType == null? "CUSTOM" : btType) + " UUID: "+ btUniqueId);
        return btUniqueId;
    }

    public static void endBusinessTransaction( String btUniqueId, String errorMessageOptional ) {
        logger.debug("Begin endBusinessTransaction btUniqueId: "+ btUniqueId +" Optional Error Message: "+ errorMessageOptional);
        if( btUniqueId == null || btUniqueId.length() == 0 ) {
            logger.warn("endBusinessTransaction requires an argument for btUniqueID");
            return;
        }
        //Transaction transaction = AppdynamicsAgent.getTransaction( btUniqueId ); //this isn't always working, let's store it locally in a static map and retrieve JBS
        Transaction transaction = transactionMap.remove(btUniqueId);
        if( transaction == null ) {
            logger.warn("Transaction not found: "+ btUniqueId);
        } else {
            if (errorMessageOptional != null && errorMessageOptional.length() > 0) {
                transaction.markAsError(errorMessageOptional);
            }
            transaction.end();
        }
        logger.debug("Finish endBusinessTransaction btUniqueId: "+ btUniqueId +" Optional Error Message: "+ errorMessageOptional);
    }

    public static void collectSnapshotData( String btUniqueId, String key, String value ) {
        collectCustomData(btUniqueId, key, value, "SNAPSHOTS");
    }

    public static void collectAnalyticsData( String btUniqueId, String key, String value ) {
        collectCustomData(btUniqueId, key, value, "ANALYTICS");
    }

    public static void collectCustomData( String btUniqueId, String key, String value, String... dataScopes ) {
        HashSet<DataScope> dataScopesSet = new HashSet<>();
        for (String ds : dataScopes) {
            if (ds.toUpperCase().equals("ANALYTICS")) dataScopesSet.add(DataScope.ANALYTICS);
            if (ds.toUpperCase().equals("SNAPSHOTS")) dataScopesSet.add(DataScope.SNAPSHOTS);
        }
        if (dataScopesSet.isEmpty()) {
            logger.warn("collectCustomData DataScopes is empty, defaulting to SNAPSHOTS");
            dataScopesSet.add(DataScope.SNAPSHOTS);
        }
        collectCustomData( btUniqueId, key, value, dataScopesSet);
    }

    public static void collectCustomData( String btUniqueId, String key, String value, Set<DataScope> dataScopesSet ){
        logger.debug("Begin collectCustomData btUniqueId: "+ btUniqueId +" data: "+ key +" = "+ value);
        //Transaction transaction = AppdynamicsAgent.getTransaction( btUniqueId );
        Transaction transaction = transactionMap.get(btUniqueId);
        if( transaction == null ) {
            logger.warn("Transaction not found: "+ btUniqueId);
        } else {
            transaction.collectData(key, value, dataScopesSet);
        }
        logger.debug("Finish collectCustomData btUniqueId: "+ btUniqueId +" data: "+ key +" = "+ value);
    }

    public static String startExitCall( String uuid, Map<String,String> callProperties, String displayName, String exitType, boolean isAsync ) {
        logger.debug("Begin startExitCall btUniqueId: "+ uuid +" name: "+ displayName +" type: "+ exitType +" isAsync: "+ isAsync);
        String exitCallCorrelationHeader = "";
        try {
            //Transaction transaction = AppdynamicsAgent.getTransaction(uuid);
            Transaction transaction = transactionMap.get(uuid);
            if (transaction == null) {
                logger.warn("Unable to start exit call transaction not found uuid: " + uuid);
                return "Error: could not create exit call, transaction not found";
            }
            ExitCall exitCall = transaction.startExitCall(callProperties, displayName, exitType, isAsync);
            exitCallCorrelationHeader = exitCall.getCorrelationHeader();
            if (exitCallCorrelationHeader == null) {
                logger.warn("Unable to start exit call for transaction: " + uuid + " name: " + displayName + " exit type: " + exitType);
                return "Error: Could not create exit call for transaction";
            }
            if (isAsync) {
                Date now = new Date();
                ProxyAgent.exitCallStash.put(exitCallCorrelationHeader, now);
                exitCall.stash(now);
            } else {
                ProxyAgent.exitCallStash.put(exitCallCorrelationHeader, exitCall);
            }
        } catch ( Exception e ) {
            logger.warn("Caught an Exception in startExitCall: "+ e,e);
        } finally {
            logger.debug("Finish startExitCall btUniqueId: "+ uuid +" name: "+ displayName +" type: "+ exitType +" isAsync: "+ isAsync +" Correlation Header: "+ exitCallCorrelationHeader);
        }
        return exitCallCorrelationHeader;
    }

    public static void endExitCall( String exitCallCorrelationHeader ) {
        if( exitCallCorrelationHeader.startsWith("Error: ") ){
            logger.warn("Error message passed instead of exit call correlation header, ignoring");
            return;
        }
        Object object = exitCallStash.remove(exitCallCorrelationHeader);
        if (object == null) {
            logger.warn("Unable to find stashed exit call for reference: " + exitCallCorrelationHeader);
            return;
        }
        ExitCall exitCall = null;
        if( object instanceof Date ) {
            exitCall = AppdynamicsAgent.fetchExitCall((Date) object);
        } else {
            exitCall = (ExitCall) object;
        }
        if( exitCall != null ) {
            logger.debug("Retrieved exitcal from agent: "+ exitCall.toString());
        }
        exitCall.end();
    }

    public static void publishEvent( String eventSummary, String severity, String eventType, Map<String,String> details ) {
        logger.debug("Begin publishEvent event summary: "+eventSummary+" severity: "+ severity +" event type: "+ eventType);
        AppdynamicsAgent.getEventPublisher().publishEvent(eventSummary, severity, eventType, details);
        logger.debug("Finish publishEvent event summary: "+eventSummary+" severity: "+ severity +" event type: "+ eventType);
    }

    public static void reportMetric( String metricName, long metricValue, String aggregationType, String timeRollupType, String clusterRollupType ) {
        logger.debug("Begin reportMetric name: "+ metricName +" = "+ metricValue +" aggregation type: "+ aggregationType + " time rollup type: "+ timeRollupType +" cluster rollup type: "+ clusterRollupType);
        AppdynamicsAgent.getMetricPublisher().reportMetric(metricName, metricValue, aggregationType, timeRollupType, clusterRollupType );
        logger.debug("Finish reportMetric name: "+ metricName +" = "+ metricValue +" aggregation type: "+ aggregationType + " time rollup type: "+ timeRollupType +" cluster rollup type: "+ clusterRollupType);
    }

    public static void main(String[] args) {
        String configFileName = "main/resources/config.properties";
        if (args.length > 0) configFileName = args[0];
        ProxyAgent proxy = new ProxyAgent(configFileName);
    }
}
