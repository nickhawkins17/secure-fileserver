# Secure File Server

This File Server/Client program is a cloud application capable of storing files on a server that a client can later 
download again. The program is comprised of a `server` directory, composed of subdirectories for each user, and a 
separate `client` directory that acts as a client within the network. 

The client directory contains the FileClient class, which can connect to the server directory to upload, download, or 
share files. It also contains the AESUtils and AESException classes, which are responsible for encrypting files before 
uploaded to the server and decrypting files when downloaded back to the client. 

The server directory contains the FileServer and Connection classes. The FileServer class manages the client-server 
connections, while the Connection class individually interfaces with each client wanting to either upload additional 
files to the directory or download files from the directory. 

The program takes confidentiality (network communication & file encryption) and integrity (server-signed certificates) 
measures to provide user security, and includes file synchronization that keeps different client environments up to 
date on changes that occur in other environments of the same user. 



## INSTRUCTIONS TO COMPILE AND RUN

The user should begin by opening two Linux terminals located within the main program directory where the `makefile` 
is located. 

The user should run the command `make runServer` in one of the terminals to compile the Connection and Server classes 
and execute the Server. The server will start listening for client connections. 

The user should run `make runClient` on the second terminal to begin a client connection. In the client terminal, the 
user is presented with username and password prompts. Verified usernames and passwords are currently defined in the 
`users.txt` document in the server directory. One of the user accounts is the username `brett` with the password 
`csProf83`. 

Once logged in, the user is presented with four options: upload, download, share, and close. When files are uploaded, a 
16 character AES key is generated and stored in the `user_fileEncryptionKeys.txt` file. This key is used to encrypt the 
file before it is sent to the server and decrypt the file when it is downloaded from the server. Users may also share 
files within server storage. 

The user can execute multiple client connections simultaneously. To create another client environment, the user should 
copy the makefile, client.truststore file, and client directory to another location. In the event this user will 
download files that are encrypted on the server, the user may also need to copy the `user_fileEncryptionKeys.txt` file 
for the user that will be logging in from this new environment. From this location, the user should run `make runClient` 
on the terminal. 

The user can run the commands `make clean` to remove java classes and `make cleanCertificate` to remove certificate 
and truststore files. 

## CERTIFICATES

Project 4 is submitted with certificates already created. However, if the user wishes to create new certificates, the 
user should run `make runCertificate` to create a self-signed server certificate, which will be accepted by the 
truststores of the server and client. This will involve prompts to set variables for two certificates and set passwords 
for source/destination keystores. When prompted, the certificate `Common name` should be set to `localhost`. 
Once this process is complete, the message `Certificate was added to keystore` will print to the console.

## KNOWN BUGS

There are a few known bugs in the program. 

1. The client occasionally hangs on download. I don't see it frequently, however it has occurred in this iteration. 
If this occurs, I recommend disconnecting and restarting the client application. 

2. When two or more clients are running, there are input/output stream problems when the same user is logged in to two 
or more client applications, causing exceptions to be thrown. There don't appear to be any problems when different 
users are logged into each client. However, since real-time file synchronization is implemented and meant to be used 
when the same user is logged into two places, this bug is a problem. Client changes are still synchronized offline, 
meaning that when the user logs in from a different location, the updated files will be pushed to the client's location. 

## AUTHOR 

This program was written by Nicholas Hawkins. 