package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.srv.BlockingConnectionHandler;
// import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImpl implements Connections<byte[]> {

    //this map contain all active client
    static ConcurrentHashMap<Integer, BlockingConnectionHandler<byte[]>> activeClient = new ConcurrentHashMap<>();

    //insert new client to the map
    @Override
    public void connect(int connectionId, BlockingConnectionHandler<byte[]> handler) {
        activeClient.put(connectionId, handler);    
    }

    //this metode call by procces and move the message to the handler that sent it to the client
    @Override
    public void send(int connectionId, byte[] msg) {
        BlockingConnectionHandler<byte[]> handler = activeClient.get(connectionId);
        handler.send(msg);
    }

    @Override
    public void disconnect(int connectionId) {
        activeClient.remove(connectionId);
    }

    @Override
    public BlockingConnectionHandler<byte[]> getHandler(int connectionId) {
        return activeClient.get(connectionId);
    }

    @Override
    public boolean clientExist (int id) {
        return activeClient.containsKey(id);
    }
}