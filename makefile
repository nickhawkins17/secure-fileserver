JCC = javac

JCR = java

JFLAGS = -g


runCertificate:
	openssl genrsa -out client.key 2048
	openssl req -x509 -new -nodes -key client.key -sha256 -days 1024 -out client.pem
	openssl pkcs12 -export -name client-cert -in client.pem -inkey client.key -out clientkeystore.p12
	
	openssl genrsa -out server.key 2048
	openssl req -x509 -new -nodes -key server.key -sha256 -days 1024 -out server.pem
	openssl pkcs12 -export -name server-cert -in server.pem -inkey server.key -out serverkeystore.p12

	keytool -importkeystore -destkeystore client.keystore -srckeystore clientkeystore.p12 -srcstoretype pkcs12 -alias client-cert
	keytool -import -alias server-cert -file server.pem -keystore client.truststore
	keytool -import -alias client-cert -file client.pem -keystore client.truststore

	keytool -importkeystore -destkeystore server.keystore -srckeystore serverkeystore.p12 -srcstoretype pkcs12 -alias server-cert
	keytool -import -alias client-cert -file client.pem -keystore server.truststore
	keytool -import -alias server-cert -file server.pem -keystore server.truststore

	

runServer:
	javac -g server/*.java
	java -classpath server FileServer

runClient:
	javac -g client/*.java
	java -classpath client FileClient

clean:

	$(RM) server/*.class
	$(RM) client/*.class

cleanCertificate:
	$(RM) *.pem
	$(RM) *.keystore
	$(RM) *.truststore
	$(RM) *.key
	$(RM) *.p12


