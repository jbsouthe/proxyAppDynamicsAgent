package com.cisco.josouthe;

import com.appdynamics.agent.api.ExitTypes;
import com.appdynamics.apm.appagent.api.DataScope;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;

public class JSONCommandParser {
    private static final Logger logger = LogManager.getLogger(JSONCommandParser.class);

    public JSONCommandParser() {

    }

    public String parse( String jsonString ) throws JSONException {
        String response = "";
        JSONObject json = new JSONObject(jsonString);
        String operation = json.optString("operation", "unknown operation");
        switch (operation) {
                /*
                { "operation": "startBusinessTransaction", "name": "BT Name Here", "singularityHeader": "optional continuing transaction correlation header", "type": "BT Type, either blank or POJO" }
                 */
            case "startBusinessTransaction": {
                response = ProxyAgent.startBusinessTransaction( json.getString("name"), json.optString("singularityHeader"), json.optString("type"));
                break;
            }
                /*
                { "operation": "endBusinessTransaction", "uuid": "Unique Id of BT", "errorMessage": "optional error message which also marks the BT as failed" }
                 */
            case "endBusinessTransaction": {
                ProxyAgent.endBusinessTransaction( json.getString("uuid"), json.optString("errorMessage"));
                response = "1";
                break;
            }
                /*
                { "operation": "collectCustomData", "uuid": "Unique Id of BT", "data": [ { "key": "the name of the data", "value": "the value of the data" } ], "dataScopes": [ "SNAPSHOTS and or ANALYTICS" ] }
                 */
            case "collectCustomData": {
                HashSet<DataScope> dataScopesSet = new HashSet<>();
                JSONArray dsArray = json.optJSONArray("dataScopes");
                for (int i=0; dsArray != null && i < dsArray.length(); i++) {
                    if (dsArray.getString(i).toUpperCase().equals("ANALYTICS")) dataScopesSet.add(DataScope.ANALYTICS);
                    if (dsArray.getString(i).toUpperCase().equals("SNAPSHOTS")) dataScopesSet.add(DataScope.SNAPSHOTS);
                }
                if (dataScopesSet.isEmpty()) {
                    logger.warn("collectCustomData DataScopes is empty, defaulting to SNAPSHOTS");
                    dataScopesSet.add(DataScope.SNAPSHOTS);
                }
                JSONArray dataArray = json.getJSONArray("data");
                for( int i=0; i < dataArray.length(); i++ ) {
                    String key = dataArray.getJSONObject(i).getString("key");
                    String value = dataArray.getJSONObject(i).getString("value");
                    ProxyAgent.collectCustomData( json.getString("uuid"), key, value, dataScopesSet);
                }
                response = new Integer( dataArray.length() ).toString();
                break;
            }
                /*
                { "operation": "publishEvent", "eventSummary": "Summary Text of Event", "severity": "INFO, WARN, ERROR" "eventType": "String Type of Event", "details": [ { "key": "the name of the detail", "value": "the value of the detail" } ] }
                eventType - the type of the event. Values allowed: [ERROR, APPLICATION_ERROR, APPLICATION_INFO, STALL, BT_SLA_VIOLATION, DEADLOCK, MEMORY_LEAK, MEMORY_LEAK_DIAGNOSTICS, LOW_HEAP_MEMORY, ALERT, CUSTOM, APP_SERVER_RESTART, BT_SLOW,
                                                                    SYSTEM_LOG, INFO_INSTRUMENTATION_VISIBILITY, AGENT_EVENT, INFO_BT_SNAPSHOT, AGENT_STATUS, SERIES_SLOW, SERIES_ERROR, ACTIVITY_TRACE, OBJECT_CONTENT_SUMMARY, DIAGNOSTIC_SESSION,
                                                                    HIGH_END_TO_END_LATENCY, APPLICATION_CONFIG_CHANGE, APPLICATION_DEPLOYMENT, AGENT_DIAGNOSTICS, MEMORY, LICENSE, CONTROLLER_AGENT_VERSION_INCOMPATIBILITY, CONTROLLER_EVENT_UPLOAD_LIMIT_REACHED,
                                                                    CONTROLLER_RSD_UPLOAD_LIMIT_REACHED, CONTROLLER_METRIC_REG_LIMIT_REACHED, CONTROLLER_ERROR_ADD_REG_LIMIT_REACHED, CONTROLLER_ASYNC_ADD_REG_LIMIT_REACHED, AGENT_METRIC_REG_LIMIT_REACHED,
                                                                    AGENT_ADD_BLACKLIST_REG_LIMIT_REACHED, AGENT_ASYNC_ADD_REG_LIMIT_REACHED, AGENT_ERROR_ADD_REG_LIMIT_REACHED, AGENT_METRIC_BLACKLIST_REG_LIMIT_REACHED, DISK_SPACE, INTERNAL_UI_EVENT,
                                                                    APPDYNAMICS_DATA, APPDYNAMICS_INTERNAL_DIAGNOSTICS, APPDYNAMICS_CONFIGURATION_WARNINGS, AZURE_AUTO_SCALING, POLICY_OPEN, POLICY_OPEN_WARNING, POLICY_OPEN_CRITICAL, POLICY_CLOSE,
                                                                    POLICY_UPGRADED, POLICY_DOWNGRADED, RESOURCE_POOL_LIMIT, THREAD_DUMP_ACTION_STARTED, EUM_CLOUD_BROWSER_EVENT, THREAD_DUMP_ACTION_END, THREAD_DUMP_ACTION_FAILED, RUN_LOCAL_SCRIPT_ACTION_STARTED,
                                                                    RUN_LOCAL_SCRIPT_ACTION_END, RUN_LOCAL_SCRIPT_ACTION_FAILED, RUNBOOK_DIAGNOSTIC_SESSION_STARTED, RUNBOOK_DIAGNOSTIC_SESSION_END, RUNBOOK_DIAGNOSTIC_SESSION_FAILED, CUSTOM_ACTION_STARTED,
                                                                    CUSTOM_ACTION_END, CUSTOM_ACTION_FAILED, WORKFLOW_ACTION_STARTED, WORKFLOW_ACTION_END, WORKFLOW_ACTION_FAILED, NORMAL, SLOW, VERY_SLOW, BUSINESS_ERROR, ALREADY_ADJUDICATED,
                                                                    ADJUDICATION_CANCELLED, EMAIL_SENT, SMS_SENT]
                 */
            case "publishEvent": {
                JSONArray detailsArray = json.optJSONArray("details");
                HashMap<String,String> details = new HashMap<>();
                for( int i=0; detailsArray != null && i < detailsArray.length(); i++ ) {
                    String key = detailsArray.getJSONObject(i).getString("key");
                    String value = detailsArray.getJSONObject(i).getString("value");
                    details.put(key,value);
                }
                ProxyAgent.publishEvent( json.getString("eventSummary"), json.getString("severity"), json.getString("eventType"), details);
                response = "1";
                break;
            }
                /*
                { "operation": "reportMetric", "name": "Name|of|Metric|Pipe|Delimited", "value": longValue(NOT_A_STRING!), "aggregationType": "Values allowed: [AVERAGE, ADVANCED_AVERAGE, SUM, OBSERVATION, OBSERVATION_FOREVERINCREASING]",
                "timeRollupType": "Values allowed: [AVERAGE, SUM, CURRENT]", "clusterRollupType": "Values allowed: [INDIVIDUAL, COLLECTIVE]" }
                 */
            case "reportMetric": {
                ProxyAgent.reportMetric( json.getString("name"), json.getLong("value"), json.getString("aggregationType"), json.getString("timeRollupType" ), json.getString("clusterRollupType") );
                response = "1";
                break;
            }
                /*
                { "operation": "startExitCall", "uuid": "Unique ID of BT", "callProperties": [ { "key": "property key", "value": "property value" } ], "displayName": "The name to show on flowmap and exit calls listing", "exitType": "optional, default is CUSTOM, else HTTP", "isAsync": true or false (false is default) }
                 */
            case "startExitCall": {
                JSONArray callPropertiesArray = json.optJSONArray("callProperties");
                HashMap<String,String> callPropertiesMap = new HashMap<>();
                for( int i=0; callPropertiesArray != null && i < callPropertiesArray.length(); i++ ) {
                    String key = callPropertiesArray.getJSONObject(i).getString("key");
                    String value = callPropertiesArray.getJSONObject(i).getString("value");
                    callPropertiesMap.put(key,value);
                }
                response = ProxyAgent.startExitCall( json.getString("uuid"), callPropertiesMap, json.getString("displayName"), json.optString("exitType", ExitTypes.CUSTOM), json.optBoolean("isAsync", false));
                break;
            }
                /*
                { "operation": "endExitCall", "exitCallId": "the long correlation id returned from startExitCall" }
                 */
            case "endExitCall": {
                ProxyAgent.endExitCall( json.getString("exitCallId"));
                response = "1";
                break;
            }
            default: {
                throw new JSONException("Unknown Operation, please check documentation and make sure you are doing this correctly");
            }
        }
        return response;
    }
}
