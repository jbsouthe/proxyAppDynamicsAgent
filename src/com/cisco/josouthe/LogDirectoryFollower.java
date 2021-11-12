package com.cisco.josouthe;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import java.util.regex.Pattern;

public class LogDirectoryFollower {
    Pattern filenamesPattern, startBTPattern, endBTPattern, errorMessagePattern, customDataPattern;
    String defaultBTName, dirName;
    File directory;
    WatchKey watchKey;

    public LogDirectoryFollower(int index, Properties props, WatchService watcher) throws ConfigurationException {
        defaultBTName=props.getProperty("logfiles."+index+".defaultBTName","LogFileBusinessTransaction");
        filenamesPattern = Pattern.compile(props.getProperty("logfiles."+index+".dirname", ".*\\.log"));
        if( props.contains("logfiles."+index+".directory") ) dirName = props.getProperty("logfiles."+index+".dirname");
        if( dirName == null ) throw new ConfigurationException("Invalid Configuration state for index "+ index +" directory must be set");
        this.directory = new File(dirName);
        if( ! directory.isDirectory() ) throw new ConfigurationException("Invalid Configuration state for index "+ index +" directory must exist");
        if( ! directory.canRead() ) throw new ConfigurationException("Invalid Configuration state for index "+ index +" directory must be readable");

        if( props.contains("logfiles."+index+".startBT") ) startBTPattern = Pattern.compile(props.getProperty("logfiles."+index+".startBT"));
        if( props.contains("logfiles."+index+".endBT") ) endBTPattern = Pattern.compile(props.getProperty("logfiles."+index+".endBT"));
        if( props.contains("logfiles."+index+".errorMessage") ) errorMessagePattern = Pattern.compile(props.getProperty("logfiles."+index+".errorMessage"));
        if( props.contains("logfiles."+index+".customData") ) customDataPattern = Pattern.compile(props.getProperty("logfiles."+index+".customData"));
        if( ! props.contains("logfiles."+index+".startBT") == props.contains("logfiles."+index+".endBT") ) throw new ConfigurationException("Invalid Configuration state for index "+ index +" either startBT and endBT are both set or both null");

        try {
            watchKey = directory.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException ioException) {
            throw new ConfigurationException("Error registering file watcher service, Exception: "+ ioException.getMessage());
        }
    }

    public WatchKey getWatchKey() { return watchKey; }

    public boolean matchesFilenames( Path filename ) {
        return filenamesPattern.matcher( filename.getFileName().toString()).matches();
    }
}
