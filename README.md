# Proxy AppDynamics Agent

You may want to read the included pdf on where this came from and who may benefit from it: 
Legacy Application Proxy Agent for AppDynamics.pdf

#Howto
1. install an appdynamics java apm agent and configure it to connect to your controller, as usual
2. configure the proxy agent app to listen for requests, as you like by editing config.properties
3. run the proxy agent with something like: java -javaagent:appdAgent/javaagent.jar -Xmx256m -jar ProxyAgent-1.0.jar config.properties
4. make requests to the proxy, feel free to play with the included perl module and example perl script: AppDynamicsProxyAgent.pm and btTest.pl

#Open an issue if something isn't working as you expect :)
