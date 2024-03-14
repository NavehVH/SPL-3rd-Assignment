package bgu.spl.net.impl.tftp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

//WA ADDED
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    static ConcurrentHashMap<Integer, String> idsLogin = new ConcurrentHashMap<>();

    private boolean shouldTerminate;

    private int connectionId;

    public Connections<byte[]> connections;

    String filesDirectory = "Files";// path of Files directory

    private byte[] dataArray;// array that store all the data we will get/send

    private byte[] blockNumberArray;// byte array with block number for upload/download

    private short blockNumber;

    private int posInDataArray;// the curent position in data array

    private String uploadedFileName;// if the client want to upload file it will be the file name

    private boolean logged;// flag to know if this client already logged in or not

    private boolean transferCompleted;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {

        this.connectionId = connectionId;
        this.connections = connections;
        this.shouldTerminate = false;
        this.dataArray = new byte[0];
        this.blockNumberArray = new byte[2];
        posInDataArray = 0;
        this.shouldTerminate = false;
        this.logged = false;
        this.transferCompleted = false;
    }

    @Override
    public void process(byte[] message) {

        short request = (short) (((short) (message[0] & 0xFF)) << 8 | (short) (message[1] & 0xFF));// convert the first
                                                                                                   // bytes in
        // the
        // packet into number

        switch (request) {
            case 1:// RRQ
                if (!logged) {
                    notlogged();
                } else {
                    downloadRequest(message);
                }
                break;

            case 2:// WRQ
                if (!logged) {
                    notlogged();
                } else {
                    uploadRequest(message);
                }
                break;

            case 3:// DATA
                getingData(message);
                break;

            case 4:// ACK
                getingAck();
                break;

            case 6:// DRQ
                if (!logged) {
                    notlogged();
                } else {
                    listRequest();
                }
                break;

            case 7:// LOGRQ
                loginRequest(message);
                break;

            case 8:// DELRQ
                if (!logged) {
                    notlogged();
                } else {
                    deleteRequest(message);
                }
                break;

            case 10:// DISC
                if (!logged) {
                    notlogged();
                } else {
                    disconnectionRequest();
                }
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public void setShouldTerminate(boolean shouldTerminate) {
        this.shouldTerminate = shouldTerminate;
    }

    private void loginRequest(byte[] message) {
        // find if this client already exist
        if (logged) {
            byte errorNumber = 7;
            byte[] error = errorMessage("User already logged in", errorNumber);
            connections.send(this.connectionId, error);
            return;
        }
        String clientName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);// client name

        // if the user not logged in, find if there is another user with the same name
        // (i maded up the error number 9)
        for (Map.Entry<Integer, String> entry : idsLogin.entrySet()) {
            if (entry.getValue().equals(clientName)) {// if the client name exist
                byte errorNumber = 9;
                byte[] error = errorMessage("Username already exist", errorNumber);
                connections.send(this.connectionId, error);
                return;
            }

        }
        logged = true;
        connections.send(this.connectionId, succsesfulAck());
        idsLogin.put(this.connectionId, clientName);
    }

    private void getingData(byte[] message) {
        // increas data size in 512;
        int add = Math.min(message.length - 6, 512);
        byte[] temp = new byte[dataArray.length + add];
        System.arraycopy(dataArray, 0, temp, 0, dataArray.length);
        dataArray = temp;

        // insert the new packet into data
        System.arraycopy(message, 6, dataArray, posInDataArray, message.length - 6);
        posInDataArray += message.length - 6;

        // send ack with curent block number
        blockNumberArray[0] = message[4];
        blockNumberArray[1] = message[5];

        connections.send(this.connectionId, acknowledgeTheClient(blockNumberArray));

        // reset block number
        blockNumberArray[0] = 0;
        blockNumberArray[1] = 0;

        // if the data transfer is complete
        if (message.length < 518) {
            try {// create new file
                Files.write(Paths.get(filesDirectory + "/" + uploadedFileName), dataArray, StandardOpenOption.CREATE);
                byte[] fileNameInBytes = uploadedFileName.getBytes();
                byte[] broadcastMessage = new byte[fileNameInBytes.length + 4];
                broadcastMessage[0] = 0;
                broadcastMessage[1] = 9;
                broadcastMessage[2] = 1;
                System.arraycopy(fileNameInBytes, 0, broadcastMessage, 3, fileNameInBytes.length);
                broadcastMessage[broadcastMessage.length - 1] = 0;
                broadcast(broadcastMessage);
            } catch (IOException e) {
                byte errorNumber = 2;
                byte[] error = errorMessage("Access violation", errorNumber);
                connections.send(this.connectionId, error);
            }
            dataArray = new byte[0];// reset the data array
            posInDataArray = 0;

        }
    }

    private void getingAck() {
        splitIntoManyPackets();
    }

    private void downloadRequest(byte[] message) {
        String fileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);// the name of the file
                                                                                             // that the client want to
                                                                                             // download
        if (!fileExist(fileName)) {// if the file doesn't exist
            byte errorNumber = 1;
            byte[] error = errorMessage("File not found", errorNumber);
            connections.send(this.connectionId, error);
            return;
        }
        Path path = Paths.get(filesDirectory + "/" + fileName);// the file path
        try {
            dataArray = Files.readAllBytes(path);// read all the file into data array
        } catch (IOException e) {
            byte errorNumber = 2;
            byte[] error = errorMessage("Access violation", errorNumber);
            connections.send(this.connectionId, error);
            return;

        }
        if (dataArray.length >= 512) {// if we can't send the data in 1 packet
            splitIntoManyPackets();
        } else {// if we can send the data in 1 packet
            short fileBytesSize = (short) dataArray.length;
            byte[] sizeInBytes = new byte[] { (byte) (fileBytesSize >> 8), (byte) (fileBytesSize & 0xff) };
            byte[] dataPacket = new byte[fileBytesSize + 6];// initialize new data packet

            // this is for op code
            dataPacket[0] = 0;
            dataPacket[1] = 3;

            // this is for data size
            dataPacket[2] = sizeInBytes[0];
            dataPacket[3] = sizeInBytes[1];

            // this is for block number
            dataPacket[4] = 0;
            dataPacket[5] = 1;
            System.arraycopy(dataArray, 0, dataPacket, 6, fileBytesSize);// insert the data into the new packet
            connections.send(this.connectionId, dataPacket);
        }
    }

    private void uploadRequest(byte[] message) {
        uploadedFileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
        if (fileExist(uploadedFileName)) {// if the file already exists, dont need to upload it
            byte errorNumber = 5;
            byte[] error = errorMessage("File already exist", errorNumber);
            connections.send(this.connectionId, error);
            return;
        }

        // send ack 0
        connections.send(this.connectionId, succsesfulAck());
    }

    private void deleteRequest(byte[] message) {
        String fileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
        if (!fileExist(fileName)) {// if the file doesn't exists we can't delete it
            byte errorNumber = 1;
            byte[] error = errorMessage("File not found", errorNumber);
            connections.send(this.connectionId, error);
            return;
        }
        Path path = Paths.get(filesDirectory + "/" + fileName);// the file path
        try {
            Files.delete(path);
        } catch (IOException e) {
            byte errorNumber = 2;
            byte[] error = errorMessage("Access violation", errorNumber);
            connections.send(this.connectionId, error);
            return;
        }
        connections.send(this.connectionId, succsesfulAck());
        byte[] fileNameInBytes = fileName.getBytes();
        byte[] broadcastMessage = new byte[fileNameInBytes.length + 4];
        broadcastMessage[0] = 0;
        broadcastMessage[1] = 9;
        broadcastMessage[2] = 0;
        System.arraycopy(fileNameInBytes, 0, broadcastMessage, 3, fileNameInBytes.length);
        broadcastMessage[broadcastMessage.length - 1] = 0;
        broadcast(broadcastMessage);
    }

    private void listRequest() {
        try {
            List<byte[]> filesBytes = new ArrayList<>();// initialize list of byte arrays

            // go over all files in Files directory
            Files.list(Paths.get(filesDirectory)).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    byte[] fileNameBytes = path.getFileName().toString().getBytes();// initialize byte array with the
                                                                                    // name of the file

                    // initialize new array and adding zero to the end
                    byte[] fileNameBytesWithSeparator = new byte[fileNameBytes.length + 1];
                    System.arraycopy(fileNameBytes, 0, fileNameBytesWithSeparator, 0, fileNameBytes.length);
                    fileNameBytesWithSeparator[fileNameBytesWithSeparator.length - 1] = 0;
                    filesBytes.add(fileNameBytesWithSeparator);// add the array to the list
                }
            });

            int totalSize = filesBytes.stream().mapToInt(arr -> arr.length).sum();// total size of all the arrays

            dataArray = new byte[totalSize];
            int currentPosition = 0;

            // insert all the byte arrays into the data array
            for (byte[] fileBytes : filesBytes) {
                System.arraycopy(fileBytes, 0, dataArray, currentPosition, fileBytes.length);
                currentPosition += fileBytes.length;
            }
        } catch (IOException e) {
            byte errorNumber = 2;
            byte[] error = errorMessage("Access violation", errorNumber);
            connections.send(this.connectionId, error);
            dataArray = new byte[0];// reset
            return;
        }
        if (dataArray.length > 512) {// if we can't send in 1 packet
            blockNumber++;
            splitIntoManyPackets();
        } else {// if we can send in 1 packet
            short allFilesBytesSize = (short) dataArray.length;
            byte[] sizeInBytes = new byte[] { (byte) (allFilesBytesSize >> 8), (byte) (allFilesBytesSize & 0xff) };
            byte[] dataPacket = new byte[allFilesBytesSize + 6];// initialize new data packet

            // this is for op code
            dataPacket[0] = 0;
            dataPacket[1] = 3;

            // tis is for data size
            dataPacket[2] = sizeInBytes[0];
            dataPacket[3] = sizeInBytes[1];

            // this is for block number
            dataPacket[4] = 0;
            dataPacket[5] = 1;
            System.arraycopy(dataArray, 0, dataPacket, 6, allFilesBytesSize);// copy the data into the new packet
            connections.send(this.connectionId, dataPacket);
            dataArray = new byte[0];// reset
        }
    }

    private void disconnectionRequest() {
        shouldTerminate = true;
        BlockingConnectionHandler<byte[]> handler = connections.getHandler(this.connectionId);
        idsLogin.remove(this.connectionId);
        connections.disconnect(this.connectionId);
        handler.send(succsesfulAck());
        try {
            handler.close();
        } catch (IOException e) {
        }
        ;

    }

    // methode to generate error message
    private byte[] errorMessage(String error, byte errorNumber) {
        byte[] messageBytes = error.getBytes(StandardCharsets.UTF_8);
        byte[] errorArray = new byte[4 + messageBytes.length + 1];
        errorArray[0] = 0;
        errorArray[1] = 5;
        errorArray[2] = 0;
        errorArray[3] = errorNumber;
        System.arraycopy(messageBytes, 0, errorArray, 4, messageBytes.length);
        errorArray[errorArray.length - 1] = 0;
        return errorArray;
    }

    // method to ack the client that we get the curent data packet
    private byte[] acknowledgeTheClient(byte[] blockNumber) {
        return new byte[] { 0, 4, blockNumber[0], blockNumber[1] };
    }

    // method that split data that bigger then 512 byte
    private void splitIntoManyPackets() {
        if (transferCompleted) {
            transferCompleted = false;
            return;
        }
        int maxByteInMessage = 512;
        transferCompleted = false;

        // decide if this is the last packet or not
        short chunkSize = (short) Math.min(dataArray.length - posInDataArray, maxByteInMessage);
        byte[] curentDataPacket = new byte[chunkSize + 6];// initialize new packet

        // this is for op code
        curentDataPacket[0] = 0;
        curentDataPacket[1] = 3;

        // this is for packet size
        byte[] dataSizeInBytes = new byte[] { (byte) (chunkSize >> 8), (byte) (chunkSize & 0xff) };
        curentDataPacket[2] = dataSizeInBytes[0];
        curentDataPacket[3] = dataSizeInBytes[1];

        // this is for block number
        blockNumberArray = new byte[] { (byte) (blockNumber >> 8), (byte) (blockNumber & 0xff) };
        curentDataPacket[4] = blockNumberArray[0];
        curentDataPacket[5] = blockNumberArray[1];

        // copy 512 or less bytes from datta array to the packet that sent right now
        System.arraycopy(dataArray, posInDataArray, curentDataPacket, 6, chunkSize);

        connections.send(this.connectionId, curentDataPacket);

        posInDataArray += chunkSize;
        blockNumber++;
        blockNumberArray = new byte[] { (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF) };

        // if this is the last packet
        if (curentDataPacket.length < 518) {

            // reset all
            transferCompleted = true;
            dataArray = new byte[0];
            blockNumber = 0;
            blockNumberArray[0] = 0;
            blockNumberArray[1] = 0;
            posInDataArray = 0;
        }
    }

    private void notlogged() {
        byte errorNumber = 6;
        byte[] error = errorMessage("User not logged in", errorNumber);
        connections.send(this.connectionId, error);

    }

    private void broadcast(byte[] message) {
        for (Integer id : idsLogin.keySet()) {
            connections.send(id, message);

        }
    }

    // method to ack the client that the request accept
    private byte[] succsesfulAck() {
        return new byte[] { 0, 4, 0, 0 };
    }


    // method for check if specific file exists in File
    private boolean fileExist(String fileName) {
        return (new File(filesDirectory, fileName).exists());
    }

}
