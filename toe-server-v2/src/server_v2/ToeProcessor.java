package server_v2;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

public class ToeProcessor implements Runnable{
	private List worklist = new LinkedList();
	
	public void processToe(ToeServer s, SocketChannel sChannel, byte[] data, int length){
		byte[] copy = new byte[length];
		System.arraycopy(data, 0, copy, 0, length);
		synchronized(worklist){
			worklist.add(new ServerDataEvent(s, sChannel, copy));
			worklist.notify();
		}
	}
	
	@Override
	public void run() {
		ServerDataEvent event;
		
		while(true){
			synchronized(worklist){
			
				while(worklist.isEmpty()){
			
					try{
						worklist.wait();
					} catch (InterruptedException e) {
					}
				}
				event = (ServerDataEvent) worklist.remove(0);
			}
			event.server.send(event.socket, event.data);
		
		}
	}
}
