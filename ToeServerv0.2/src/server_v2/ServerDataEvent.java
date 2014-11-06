package server_v2;

import java.nio.channels.SocketChannel;

public class ServerDataEvent {
	public ToeServer server;
	public SocketChannel socket;
	public byte[] data;
	
	public ServerDataEvent(ToeServer s, SocketChannel sock, byte[] d){
		server = s;
		socket = sock;
		data = d;
	}

}
