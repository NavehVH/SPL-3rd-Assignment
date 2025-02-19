package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TftpClientProtocol {

    private byte[] dataArray = new byte[0];// array that store all the data we will get/send

    private byte[] blockNumberArray = new byte[2];// byte array with block number for upload/download

    private short blockNumber = 1;

    private int posInDataArray;// the curent position in data array

    private String uploadFileName;// if the client want to upload file it will be the file name

    private String downloadedFileName;// if the client want to upload file it will be the file name

    String filesDirectory = "./";// path of Files directory

    private boolean askForList = false;

    private boolean askForFile = false;

    private boolean askForUploade = false;

    private boolean askForLogin = false;

    private boolean askForDelete = false;

    private boolean askForDisc = false;

    private boolean completed = false;

    private boolean shouldTerminate = false;

    private TftpClient client;

    private boolean loggedIn = false;


    public TftpClientProtocol(TftpClient client) {
        this.client = client;
    }

    public void keyboardProcess(String input) {
        String[] words = input.split("\\s+");
        String firstWord = words[0];
        char invalid;

        // check if we are in another process
        if (dataArray.length > 0) {
            System.out.println("Wait, another request is being processed");
            return;
        }
        switch (firstWord) {

            case "LOGRQ":
                if (input.length() < 7) {// if there is no username
                    System.out.println("Invalid input");
                    break;
                }

                if (input.charAt(6) == ' ') {// if username start with space
                    System.out.println("Invalid input");
                    break;
                }

                if (!loggedIn) {
                    askForLogin = true;
                }
                String userName = input.substring(6);
                byte[] userNameInBytes = userName.getBytes();
                byte[] logPacket = new byte[userNameInBytes.length + 3];
                logPacket[0] = 0;
                logPacket[1] = 7;
                logPacket[logPacket.length - 1] = 0;
                System.arraycopy(userNameInBytes, 0, logPacket, 2, userNameInBytes.length);
                client.send(logPacket);
                break;

            case "DELRQ":
                if (input.length() < 7) {// if there is no file name
                    System.out.println("Invalid input");
                    break;
                }
                String fileName = input.substring(6);
                byte[] fileNameInBytes = fileName.getBytes();
                byte[] delPacket = new byte[fileNameInBytes.length + 3];
                delPacket[0] = 0;
                delPacket[1] = 8;
                delPacket[delPacket.length - 1] = 0;
                System.arraycopy(fileNameInBytes, 0, delPacket, 2, fileNameInBytes.length);
                askForDelete = true;
                client.send(delPacket);
                break;

            case "RRQ":
                if (input.length() > 4) {// if the file name start with space
                    if (input.charAt(4) == ' ') {
                        System.out.println("Invalid input");
                        break;
                    }
                }
                if (input.length() < 5) {// if there is no file name
                    System.out.println("Invalid input");
                    break;
                }
                String readFileName = input.substring(4);
                Path readPath = Paths.get(readFileName);
                if (Files.exists(readPath)) {
                    System.out.println("Error 5 File Already exists");
                } else {
                    byte[] readFileNameInBytes = readFileName.getBytes();
                    byte[] rrqPacket = new byte[readFileNameInBytes.length + 3];
                    rrqPacket[0] = 0;
                    rrqPacket[1] = 1;
                    rrqPacket[rrqPacket.length - 1] = 0;
                    System.arraycopy(readFileNameInBytes, 0, rrqPacket, 2, readFileNameInBytes.length);
                    askForFile = true;
                    downloadedFileName = readFileName;
                    client.send(rrqPacket);
                }
                break;

            case "WRQ":
                if (input.length() > 4) {// if the file name start with space
                    if (input.charAt(4) == ' ') {
                        System.out.println("Invalid input");
                        break;
                    }
                }
                if (input.length() < 5) {// if there is no file name
                    System.out.println("Invalid input");
                    break;
                }
                String writeFileName = input.substring(4);
                Path writePath = Paths.get(writeFileName);
                if (!Files.exists(writePath)) {
                    System.out.println("Error 1 File not found");
                } else {
                    byte[] writeFileNameInBytes = writeFileName.getBytes();
                    byte[] wrqPacket = new byte[writeFileNameInBytes.length + 3];
                    wrqPacket[0] = 0;
                    wrqPacket[1] = 2;
                    wrqPacket[wrqPacket.length - 1] = 0;
                    System.arraycopy(writeFileNameInBytes, 0, wrqPacket, 2, writeFileNameInBytes.length);
                    askForUploade = true;
                    uploadFileName = writeFileName;
                    client.send(wrqPacket);
                }
                break;

            case "DIRQ":
                byte[] dirQ = new byte[] { 0, 6 };
                askForList = true;
                client.send(dirQ);
                break;

            case "DISC":
                byte[] disc = new byte[] { 0, 10 };
                client.send(disc);
                askForDisc = true;
                break;

            default:
                System.out.println("Invalid input");
                break;

        }
    }

    public void listeningProcess(byte[] message) {

        short request = (short) (((short) (message[0] & 0xFF)) << 8 | (short) (message[1] & 0xFF));// convert the first
                                                                                                   // bytes in the
                                                                                                   // packet into number

        switch (request) {

            case 3:// DATA
                getingData(message);
                break;

            case 4:// ACK
                short number = (short) (((short) (message[2] & 0xFF)) << 8 | (short) (message[3] & 0xFF));
                System.out.println("ACK " + number);
                getingAck(message);
                break;

            case 5:// ERROR
                getingError(message);
                break;

            case 9:// BCAST
                getingBcast(message);
                break;
        }
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
        sendAck(blockNumberArray);

        // reset block number
        blockNumberArray[0] = 0;
        blockNumberArray[1] = 0;

        // if the data transfer is complete
        if (message.length < 518) {
            if (askForFile) {
                try {// create new file
                    Files.write(Paths.get(downloadedFileName), dataArray);
                    System.out.println("RRQ " + downloadedFileName + " complete");

                } catch (IOException e) {
                }
                askForFile = false;
            } else if (askForList) {
                printList(dataArray);
                askForList = false;
            }
            dataArray = new byte[0];// reset the data array
            posInDataArray = 0;
        }
    }

    private void getingAck(byte[] message) {
        if (askForLogin) {
            // System.out.println("ACK " + 0);
            askForLogin = false;
            loggedIn = true;
        } else if (askForUploade) {
            // System.out.println("ACK " + 0);
            askForUploade = false;
            uploadFile();
        } else if (askForDelete) {
            // System.out.println("ACK " + 0);
            askForDelete = false;
        } else if (askForDisc) {
            shouldTerminate = true;
        } else {
            if (!completed) {

                splitIntoManyPackets();
            } else {
                completed = false;
                System.out.println("WRQ " + uploadFileName + " complete");
            }
        }

    }

    private void getingError(byte[] message) {
        String error = new String(message, 4, message.length - 4);// MAYBE -6
        System.out.println("Error " + message[3] + " " + error);

        // the request was denied so we need to ask again
        askForDelete = false;
        askForFile = false;
        askForUploade = false;
        askForList = false;
        askForLogin = false;
    }

    private void getingBcast(byte[] message) {
        String status = "";
        if (message[2] == 0) {
            status = "delete";
        } else if (message[2] == 1) {
            status = "add";
        }
        String fileName = new String(message, 3, message.length - 3);// MAYBE -5
        System.out.println("BCAST " + status + " " + fileName);
    }

    private void uploadFile() {
        Path path = Paths.get(uploadFileName);// the file path
        try {
            dataArray = Files.readAllBytes(path);// read all the file into data array
        } catch (IOException e) {
            return;
        }
        if (dataArray.length >= 512) {// if we can't send the data in 1 packet
            splitIntoManyPackets();
        } else {// if we can send the data in 1 packet
            short fileBytesSize = (short) dataArray.length;
            byte[] sizeInBytes = new byte[] { (byte) (fileBytesSize >> 8), (byte) (fileBytesSize & 0xFF) };
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
            client.send(dataPacket);
            dataArray = new byte[0];
            completed = true;
        }
    }

    private void sendAck(byte[] message) {
        byte[] ack = new byte[4];
        System.arraycopy(message, 0, ack, 2, message.length);
        ack[0] = 0;
        ack[1] = 4;
        client.send(ack);
    }

    private void splitIntoManyPackets() {
        int maxByteInMessage = 512;

        // decide if this is the last packet or not
        short chunkSize = (short) Math.min(dataArray.length - posInDataArray, maxByteInMessage);
        byte[] curentDataPacket = new byte[chunkSize + 6];// initialize new packet

        // this is for op code
        curentDataPacket[0] = 0;
        curentDataPacket[1] = 3;

        // this is for packet size
        byte[] dataSizeInBytes = new byte[] { (byte) (chunkSize >> 8), (byte) (chunkSize & 0xFF) };
        curentDataPacket[2] = dataSizeInBytes[0];
        curentDataPacket[3] = dataSizeInBytes[1];

        // this is for block number
        blockNumberArray = new byte[] { (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF) };
        curentDataPacket[4] = blockNumberArray[0];
        curentDataPacket[5] = blockNumberArray[1];

        // copy 512 or less bytes from datta array to the packet that sent right now
        System.arraycopy(dataArray, posInDataArray, curentDataPacket, 6, chunkSize);

        client.send(curentDataPacket);

        posInDataArray += chunkSize;
        blockNumber++;
        // if this is the last packet
        if (curentDataPacket.length < 518) {

            // reset all
            dataArray = new byte[0];
            blockNumber = 1;
            blockNumberArray[0] = 0;
            blockNumberArray[1] = 0;
            posInDataArray = 0;
            completed = true;
        }
    }

    private void printList(byte[] message) {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (byte b : message) {
            if (b == 0) {
                // String fileName = buffer.toString(StandardCharsets.UTF_8);
                String fileName = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                System.out.println(fileName);
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }

        if (buffer.size() > 0) {
            // String text = buffer.toString(StandardCharsets.UTF_8);
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            System.out.println(text);
        }
    }

    public boolean getAskForDisc() {
        return askForDisc;
    }

    public boolean getShouldTerminate() {
        return shouldTerminate;
    }
}
