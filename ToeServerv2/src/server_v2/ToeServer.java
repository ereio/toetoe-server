package server_v2;
/*
 * Written with help of the tutorial found at http://rox-xmlrpc.sourceforge.net/niotut/
 *  */

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ToeServer implements Runnable {
	private final static String TAG = "ToeServer";
	private InetAddress host;
	private int port;
	private ToeProcessor tProcessor;
	
	private Selector selector;
	private ServerSocketChannel socketChannel;
	private ByteBuffer mainBuffer = ByteBuffer.allocate(1024);
	
	private List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();
	private Map<SocketChannel, List> pendingData = new HashMap<SocketChannel, List>();
	
	public ArrayList<Socket>clients = new ArrayList<Socket>();
	
	public ToeServer(ToeProcessor tpro) throws IOException{
			host = InetAddress.getByName("0.0.0.0");
			port = 40052;
			selector = initSelector();
			tProcessor = tpro;
	}
	
	public void send(SocketChannel sock, byte[] d){
		// Handling Changes
		synchronized (pendingChanges) {
			pendingChanges.add(new ChangeRequest(sock, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));
			
			// Handling Changes Data
			synchronized(pendingData) {
				List<ByteBuffer> queue = pendingData.get(sock);
				if (queue == null){
					queue = new ArrayList<ByteBuffer>();
					pendingData.put(sock, queue);
				}
				queue.add(ByteBuffer.wrap(d));
			}	
		}
		selector.wakeup();
	}
	
	
	@Override
	public void run() {
		try{
			// MAIN SERVER LOOP
			while(true){
				synchronized(pendingChanges) {
					Iterator changes = pendingChanges.iterator();
					while (changes.hasNext()) {
						ChangeRequest change = (ChangeRequest) changes.next();
						switch(change.type){
						case ChangeRequest.CHANGEOPS:
							SelectionKey key = change.socket.keyFor(selector);
							key.interestOps(change.ops);
						}
					}
					pendingChanges.clear();
				}
				
				
				// SELECT EVENT KEYS
				selector.select();
				Iterator allKeys = selector.selectedKeys().iterator();
				
				// MAIN KEY LOOP 
				while(allKeys.hasNext()){
					SelectionKey curKey = (SelectionKey) allKeys.next();
					allKeys.remove();
					
					if(!curKey.isValid()){
						continue;
					}
					
					if(curKey.isAcceptable()){
						accept(curKey);
					} else if(curKey.isReadable()){
						read(curKey);
					} else if(curKey.isWritable()){
						write(curKey);
					}
					
				} // END KEY LOOP
			} // END SERVER LOOP
			
		}catch (IOException e){
			e.printStackTrace();
		}
	} // END RUN LOOP
	
	private Selector initSelector() throws IOException{
		Selector socketSelector = SelectorProvider.provider().openSelector();
		socketChannel = ServerSocketChannel.open();
		socketChannel.configureBlocking(false);
		
		InetSocketAddress address = new InetSocketAddress(host, port);
		socketChannel.socket().bind(address);
		
		socketChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
		return socketSelector;
	}
	
	// Accepts the connection and establishes the socket for the connection
	private void accept(SelectionKey k){
		ServerSocketChannel ssChannel = (ServerSocketChannel) k.channel();
		try{
			
			SocketChannel sChannel = ssChannel.accept();
			clients.add(sChannel.socket());
			sChannel.configureBlocking(false);
			sChannel.register(selector, SelectionKey.OP_READ);
			
		} catch (IOException e){
			e.printStackTrace();
		}
		
	}
	
	// Reads incoming data from the socketChannel connection
	private void read(SelectionKey k){
		SocketChannel sChannel = (SocketChannel) k.channel();
		mainBuffer.clear();
		int bytes;
		
		// Test Buffer Read, Connection may have closed
		try{
			bytes = sChannel.read(mainBuffer);
		} catch (IOException e){
			k.cancel();
			try {
			sChannel.close();
			} catch (IOException e1) { 
				e1.printStackTrace();
			}
			return;
		}
		
		if(bytes == -1){
			try {
				k.channel().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			k.cancel();
			return;
		}
		tProcessor.processToe(this, sChannel, mainBuffer.array(), bytes);
	}
	
	// Writes outgoing data to the socketChannel connection
	private void write(SelectionKey k){
		SocketChannel sChannel = (SocketChannel) k.channel();
		
		synchronized (pendingData) {
			List queue = (List) pendingData.get(sChannel);
			
			// Handles all pending data being sent 
			while(!queue.isEmpty()){
				ByteBuffer subBuf = (ByteBuffer) queue.get(0);
				try {
					sChannel.write(subBuf);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if( subBuf.remaining() > 0 ){ return; }
				queue.remove(0);
			}
			
			if(queue.isEmpty()) {
					k.interestOps(SelectionKey.OP_READ);
			}
			
		}
		
	}
}
