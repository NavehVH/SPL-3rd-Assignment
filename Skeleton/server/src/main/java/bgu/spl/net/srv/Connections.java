package bgu.spl.net.srv;

// import java.io.IOException;

public interface Connections<T> {

    void connect(int connectionId, BlockingConnectionHandler<T> handler);
    void send(int connectionId, T msg);
    void disconnect(int connectionId);
    public BlockingConnectionHandler<byte[]> getHandler(int connectionId);
    public boolean clientExist (int id);
}
