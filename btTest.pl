#!/usr/bin/perl -I.
#test to start a BT run for a bit and then end it
$url="http://localhost:8888/agent";

use AppDynamicsProxyAgent;
AppDynamicsProxyAgent::init("http://localhost:8888/agent","curl");


AppDynamicsProxyAgent::publishEvent("Starting custom BT generation script","INFO","APPLICATION_INFO");
while(1) {
	$sleepTime = int(rand(9))+1;
	$uuid = AppDynamicsProxyAgent::startTransaction("Some Crazy Transaction");
	AppDynamicsProxyAgent::customData($uuid,"Sleep Time",$sleepTime);
	AppDynamicsProxyAgent::customData($uuid,"Perl Script",$0);
	AppDynamicsProxyAgent::customData($uuid,"Perl Process ID",$$);
	sleep($sleepTime/2);
	$exitCallOne = AppDynamicsProxyAgent::startExitCall($uuid, "Synchronous Exit Call", "false");
#	$exitCallTwo = AppDynamicsProxyAgent::startExitCall($uuid, "Async Exit Call", "true");
#	sleep(1);
#	AppDynamicsProxyAgent::endExitCall( $exitCallTwo );
	sleep($sleepTime/2);
	AppDynamicsProxyAgent::endExitCall( $exitCallOne );
	AppDynamicsProxyAgent::endTransaction($uuid);
}
