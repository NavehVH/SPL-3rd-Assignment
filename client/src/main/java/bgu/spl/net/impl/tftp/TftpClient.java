package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class TftpClient {

    static private DataOutputStream out;

    static private BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));

    static private TftpClientProtocol protocol;
    static private TftpClientEncoderDecoder encdec;

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket("localhost", 7777)) {
            System.out.println("Client started");
            out = new DataOutputStream(socket.getOutputStream());
            protocol = new TftpClientProtocol(new TftpClient());
            encdec = new TftpClientEncoderDecoder();
            Thread listeningThread = new Thread(() -> {
                try {
                    InputStream inputFromServer = socket.getInputStream();
                    int read;
                    while ((read = inputFromServer.read()) >= 0 && !protocol.getShouldTerminate()) {
                        byte[] nextMessage = encdec.decodeNextByte((byte) read);
                        if (nextMessage != null) {
                            protocol.listeningProcess(nextMessage);

                        }
                    }
                } catch (IOException e) {
                }

            });
            listeningThread.start();

            // "keyboard thread" - main thread
            while (!protocol.getShouldTerminate()) {
                if (!protocol.getAskForDisc()) {// if the client ask for disconnect dont wait for readline method, skip
                                                // this to check the ack
                    String input = userIn.readLine();
                    protocol.keyboardProcess(input);
                }
            }
            listeningThread.interrupt();
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] message) {
        synchronized (out) {
            try {
                if (message != null) {
                    out.write(encdec.encode(message));
                    out.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
