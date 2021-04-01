package AppDynamicsProxyAgent;

#AppDynamicsProxyAgent.pm to forwards requests to a java proxy agent running on the same machine or otherwise network accessible
#Author: John Southerland josouthe@cisco.com
#Date: 02/17/2021
#Examples:
# use AppDynamicsProxyAgent;
# AppDynamicsProxyAgent::init("http://localhost:8888/agent","/usr/bin/curl");
# $transactionUUID = AppDynamicsProxyAgent::startTransaction("BT Name");
# do some actual transaction stuff, then call:
# AppDynamicsProxyAgent::endTransaction( $transactionUUID );

use strict;
use warnings;

our $url="http://localhost:8888/agent";
our $curlCMD="curl";
our $curl=$curlCMD . " -s -X POST --header \"Content-Type: application/json\" --data ";

sub init {
	my($urlARG,$curlARG) = @_;
	$url = $urlARG;
	$curlCMD = $curlARG;
	$curl=$curlCMD  . " -s -X POST --header \"Content-Type: application/json\" --data ";
}

sub startTransaction {
	my $btName = $_[0];
	my $uuid="";
	open(CMD," $curl \'{ \"operation\": \"startBusinessTransaction\", \"name\": \"$btName\" }\' $url |"); 
	while(<CMD>) {
		print "start bt uuid: $_\n";
		$uuid=$_;
	}
	close CMD;
	return $uuid;
}

sub endTransaction {
	my($uuid,$errorMessage) = @_;
	open(CMD," $curl \'{ \"operation\": \"endBusinessTransaction\", \"uuid\": \"$uuid\" }\' $url |");
	while(<CMD>) {
		print "end bt $uuid: $_\n";
	}
	close(CMD);
}

sub startExitCall {
	my $uuid=$_[0];
	my $name = $_[1];
	my $isAsync = $_[2];
	my $exitcall="";
	open(CMD," $curl \'{ \"operation\": \"startExitCall\", \"uuid\": \"$uuid\", \"callProperties\": [{ \"key\": \"host\", \"value\":\"SomeHostName\"}, {\"key\": \"port\", \"value\":\"80\"} ] , \"displayName\": \"$name\", \"isAsync\": $isAsync }\' $url |"); 
	while(<CMD>) {
		print "start exitcall exitCallId: $_\n";
		$exitcall=$_;
	}
	close CMD;
	return $exitcall;
}

sub endExitCall {
	my ($exitCall) = @_;
	open(CMD," $curl \'{ \"operation\": \"endExitCall\", \"exitCallId\": \"$exitCall\" }\' $url |");
	while(<CMD>) {
		print "end exitcall $exitCall: $_\n";
	}
	close(CMD);
}

sub customData {
	my ($uuid,$key,$value,$dataScopes) = @_;
	my $dsJSONList = "\"SNAPSHOTS\""; #default and always snapshot, cheap and easy
	if( defined $dataScopes && $dataScopes =~ /analytics/i ) {
		$dsJSONList .= ", \"ANALYTICS\"";
	}
	open(CMD," $curl \'{ \"operation\": \"collectCustomData\", \"uuid\": \"$uuid\", \"data\": [ {\"key\": \"$key\", \"value\": \"$value\"} ], \"dataScopes\": [ " . $dsJSONList ." ] }\' $url |");
	while(<CMD>) {
		print "bt custom data $uuid $key=$value : $_\n";
	}
	close(CMD);
}

sub publishEvent {
	my ($eventSummary,$severity,$type) = @_;
	open(CMD," $curl \'{ \"operation\": \"publishEvent\", \"eventSummary\": \"$eventSummary\", \"severity\": \"$severity\", \"eventType\": \"$type\" }\' $url |");
	while(<CMD>) {
		print "published event \"$eventSummary\" of severity $severity and type $type : $_\n";
	}
	close(CMD);
}

1;
