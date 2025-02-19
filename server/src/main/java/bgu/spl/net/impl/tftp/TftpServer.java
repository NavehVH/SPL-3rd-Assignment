package bgu.spl.net.impl.tftp;

// import java.io.IOException;
import java.net.ServerSocket;
// import java.net.Socket;
// import java.sql.Connection;

import java.util.function.Supplier;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.Server;


public class TftpServer<T> {

    private final int port;
    private final Supplier<BidiMessagingProtocol<byte[]>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<byte[]>> encdecFactory;
    private ServerSocket sock;

    public TftpServer(
        int port,
        Supplier<BidiMessagingProtocol<byte[]>> protocolFactory,
        Supplier<MessageEncoderDecoder<byte[]>> encdecFactory) {
            this.port = port;
            this.protocolFactory = protocolFactory;
            this.encdecFactory = encdecFactory;
    
    }

    //the server start here
    public static void main(String[] args) {
        int port = 7777;
        Supplier<BidiMessagingProtocol<byte[]>> protocolFactory = () -> new TftpProtocol();//initialize protocol factory
        Supplier<MessageEncoderDecoder<byte[]>> encdecFactory = () -> new TftpEncoderDecoder();//initialize encoder decoder factory
        ConnectionsImpl connections = new ConnectionsImpl();//initialize new connections to store all the active client
        Server<byte[]> server = threadPerClient(port, protocolFactory, encdecFactory, connections);//build new server
         
            //after that we create new Base server and new connection handler tho start method in BaseServer called
            server.serve();//Start the server.
    }
    
    //this method return BaseServer object, this object will be the server
    public static <T> Server<T>  threadPerClient(
            int port,
            Supplier<BidiMessagingProtocol<T> > protocolFactory,
            Supplier<MessageEncoderDecoder<T> > encoderDecoderFactory,
            Connections<T> connections) {

        return new BaseServer<T>(port, protocolFactory, encoderDecoderFactory, connections) {
            
            //this ia an anonymous inner class its override the BaseServer execute
            //this method called by the loop in BaseServer class when new client try to connect to the server
            //it will create new connection handler for the new client and start handeling the client in BlocingConnectionHandler loop
            @Override
            protected void execute(BlockingConnectionHandler<T>  handler) {
                new Thread(handler).start();//
            }
        };

    }

}

    
