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
#Date: 11/10/2021 Changes: converted from curl to raw tcp sockets to better support iSeries without curl

#use strict;
#use warnings;
use Socket;

our $url="http://localhost:8888/agent";
our $host="localhost";
our $port=8888;
our $uri="/agent";
#$curlCMD . " -s -X POST --header \"Content-Type: application/json\" --data ";

sub init {
	my($urlARG) = @_;
	$url = $urlARG;
	($host,$port,$uri) = _parseURL($url);
	#print "Initialized $host $port $uri\n";
}

sub _parseURL {
	my $url = $_[0];
	my $host = "localhost";
	my $port = 80;
	my $uri = "/agent";

	if( $url =~ /https:\/\// ) { $port=443; }
	if( $url =~ /http[s]?:\/\/\S+:(\d+)\// ) { $port=$1; }
	if( $url =~ /http[s]?:\/\/([^:\/]+)[\d+](\/\S+)$/ ) {
		$host=$1;
		$uri=$2;
	}
	return ($host, $port, $uri);
}

sub _openTCP {
	# get parameters
	my ($FS, $dest, $port) = @_;

	my $proto = getprotobyname('tcp');
	socket($FS, PF_INET, SOCK_STREAM, $proto);
	my $sin = sockaddr_in($port,inet_aton($dest));
	connect($FS,$sin) || return undef;

	my $old_fh = select($FS);
	$| = 1; 		        # don't buffer output
	select($old_fh);
	return 1;
}

sub _sendRequest {
	my $data = $_[0];

	if( _openTCP(SH, $host, $port) == undef ) {
		print "Error connecting to proxy!\n";
		exit(-1);
	}

	print SH "POST $uri HTTP/1.0\r\n";
	print SH "Content-Type: application/json\r\n";
	print SH "Content-length: ". length($data) ."\r\n";
	print SH "\r\n$data\r\n\r\n";
	#print "sent: $data\n";

	my $response = "";
	my $buffer = "";
	while( <SH> ) {
		next if( $_ =~ /^$/ );
		next if( $_ =~ /^HTTP/ );
		next if( $_ =~ /^Connection: / );
		next if( $_ =~ /^Date: / );
		next if( $_ =~ /^Content-length: / );
		$_ =~ s/^[\r\n]*//;
		$response .= $_;
	}
	#print "received: $response\n";
	close(SH);
	return $response;
}

sub startTransaction {
	my $btName = $_[0];
	my $uuid = _sendRequest("{ \"operation\": \"startBusinessTransaction\", \"name\": \"$btName\" }");
	return $uuid;
}

sub endTransaction {
	my($uuid,$errorMessage) = @_;
	_sendRequest("{ \"operation\": \"endBusinessTransaction\", \"uuid\": \"$uuid\" }");
}

sub startExitCall {
	my $uuid=$_[0];
	my $name = $_[1];
	my $isAsync = $_[2];
	my $exitcall= _sendRequest("{ \"operation\": \"startExitCall\", \"uuid\": \"$uuid\", \"callProperties\": [{ \"key\": \"host\", \"value\":\"SomeHostName\"}, {\"key\": \"port\", \"value\":\"80\"} ] , \"displayName\": \"$name\", \"isAsync\": $isAsync }");
	return $exitcall;
}

sub endExitCall {
	my ($exitCall) = @_;
	_sendRequest("{ \"operation\": \"endExitCall\", \"exitCallId\": \"$exitCall\" }");
}

sub customData {
	my ($uuid,$key,$value,$dataScopes) = @_;
	my $dsJSONList = "\"SNAPSHOTS\""; #default and always snapshot, cheap and easy
	if( defined $dataScopes && $dataScopes =~ /analytics/i ) {
		$dsJSONList .= ", \"ANALYTICS\"";
	}
	_sendRequest("{ \"operation\": \"collectCustomData\", \"uuid\": \"$uuid\", \"data\": [ {\"key\": \"$key\", \"value\": \"$value\"} ], \"dataScopes\": [ " . $dsJSONList ." ] }");
}

sub publishEvent {
	my ($eventSummary,$severity,$type) = @_;
	_sendRequest("{ \"operation\": \"publishEvent\", \"eventSummary\": \"$eventSummary\", \"severity\": \"$severity\", \"eventType\": \"$type\" }");
}

1;
