package xyz.osei.sensor.senders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketSender implements Sender {

    private Socket socket;

    public SocketSender(String host, int port) {
        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            socket = new Socket(address.getAddress(), address.getPort());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendEvent(String data) throws IOException {
        socket.getOutputStream().write(data.getBytes());
    }
}
