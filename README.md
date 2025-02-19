# Extended TFTP Implementation
BGU System Programming (SPL) 3rd assignment. Written in Java and build with Maven. 

This is an implementation [extended TFTP protocol](https://en.wikipedia.org/wiki/Trivial_File_Transfer_Protocol) (Trivial File Transfer Protocol)  and an implementation of a server that utilize the protocol for bi-directional file transfer between a server and multiple clients.

# Running the Project
1. **Clone the Repository**
2. **Build the Project**: using Maven
> mvn clean compile
> mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="PORT"
3. **Run the Server**: 
> java -jar ./server.jar PORT
4. **Run the client**
>  ./client/Tftpclinet IP PORT

The original repo for the Rust client provided by the course staff is available at [The course Github repo](https://github.com/bguspl/TFTP-rust-client )

# Project Overview
## The Extended TFTP Protocol
The TFTP server is a file transfer protocol allowing multiple users to upload and download files from the server and announce when files are added or deleted to the server. The communication between the server and the client(s) is be performed using a binary communication protocol, which support the upload, download, and lookup of files.

## The Protocol Features
- **User Authentication**: Users can log in to the server by providing a username that uniuqly identifies them, and log out at the end of use.
- **File Operations**: Users can read files from and write files into the server, and delete files from the server.
- **Directory Listing**: Users can perform files lookup.
- **Broadcast Notifications**: The server broadcasts notifications to all logged-in users when files are added or deleted.
- **TCP Usage**: unlink the original TFTP protocol, which is based on UDP - This implementation is based on TCP.

## The Server 
The implementation of the server is based on the Thread-Per-Client (TPC) architecture, which creates a working thread for every client connecting to the server. The server itself is generic and have no dependence of the specific protocol.

The server's comminication is based on [TCP](https://en.wikipedia.org/wiki/Transmission_Control_Protocol) (Transmission Control Protocol) as a design choice for a session orianted commonication.

## Usage
The clients can interact with the server with following commands:

- **LOGRQ**: Login to the server with a username. two clients can't have the same username.
  - Format: `LOGRQ <Username>`
  - Example: `LOGRQ Puck`

- **DELRQ**: Delete a file from the server.
  - Format: `DELRQ <Filename>`
  - Example: `DELRQ example.txt`

- **RRQ**: Download a file from the server to the current directory.
  - Format: `RRQ <Filename>`
  - Example: `RRQ example.txt`

- **WRQ**: Upload a file from the current directory to the server.
  - Format: `WRQ <Filename>`
  - Example: `WRQ example.txt`

- **DIRQ**: List all filenames available on the server.
  - Format: `DIRQ`

- **DISC**: Disconnect from the server and exit the client. freeing the client's usename.
-   Format: `DISC`

## More Background Operations
- **Data**: The Data packets that transfer information or files between machines. Max size per pakcet: 518 Bytes.

- **ACK**: used to acknowledge different packets, send to the sender after every packet is received. (and between data packets for a large file)

- **BCAST**: used to notify all logged-in clients that a file was deleted/added. This is a Server to client message only.

- **ERROR** sent in case some error have occoured. list of errors:
  
![error info](/Images/error%20info.png)

## Running example
Is available [here](SPL241__Assignment_3_instructions_v1.7.pdf) at pages 12-13

## System Requirements
- Java 
- Maven version 3.6.3 or later
- Stable internet connection (or LAN connection)
