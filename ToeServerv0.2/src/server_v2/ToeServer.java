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
import java.nio.channels.ClosedChannelException;
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
	private int mainBufSize;
	private boolean DEBUG_first = true;
	
	private List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();
	private Map<SocketChannel, List> pendingData = new HashMap<SocketChannel, List>();
	
	public ArrayList<Socket>clients = new ArrayList<Socket>();
	public Map<String, Socket>clientMap = new HashMap<String, Socket>();
	
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
			
			// Handling pending Data sent to the server
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
				// Queues pending changes to the channels requesting io
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
						DEBUG_setKeyToWrite(curKey);
						
					} else if(curKey.isWritable()){
						//write(curKey);
						DEBUG_broadCastReads(curKey, mainBuffer, mainBufSize);
						
					}
					else{}
					
				} // END KEY LOOP
			} // END SERVER LOOP
			
		}catch (IOException e){
			e.printStackTrace();
		}
	} // END RUN LOOP
	
	// Creates selector for the socket
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
			sChannel.configureBlocking(false);
			sChannel.register(selector, SelectionKey.OP_READ);
			clients.add(sChannel.socket());
			
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
			mainBufSize = bytes;
			DEBUG_readBytes(mainBuffer, bytes);
			
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
				System.out.println(subBuf.array().toString());
				
				try {
					// Actually writes the buffer for the outgoing data to channel
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
	
	private void DEBUG_readBytes(ByteBuffer bb, int bSize){
		ByteBuffer byteMessage = parseByteBuffer(bb, bSize);
		if(byteMessage != null){
		byte[] bArray = byteMessage.array();
		
		// Creates a String from the byte array created from a bytebuffer
		String message = new String(bArray);
		
		System.out.println("ORGINAL BB" + bb);
		System.out.println("TEMP BB:" + byteMessage);
		System.out.println("bArray to String: " + message);
		System.out.println("MainBuffer size: " + bSize);
		System.out.println("byteMessage size: " + byteMessage.capacity());
		}
	}
	
	private ByteBuffer parseByteBuffer(ByteBuffer bb, int bSize){
		if( bSize != -1){
		ByteBuffer temp = ByteBuffer.allocate(bSize);
		
		bb.rewind();
		for(int b = 0; b < bSize; b++){
			temp.put(bb.get());
			}
		
		return temp;
		}
		else { return null; }
	}
	
	private void DEBUG_setKeyToWrite(SelectionKey key){
		SocketChannel sChannel = (SocketChannel) key.channel();
		try {
			sChannel.register(selector, SelectionKey.OP_WRITE);
		} catch (ClosedChannelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void DEBUG_broadCastReads(SelectionKey key, ByteBuffer buffer, int bytesRead){
		 SocketChannel channel = (SocketChannel)key.channel();
		 final byte[] WELCOME = "Welcome From The Server!".getBytes(Charset.forName("UTF-8"));;
		 int bytes;
		 DEBUG_first = false;
		 ByteBuffer temp = ByteBuffer.allocate(bytesRead);
		 
		 buffer.rewind();
		 
		 for(int b = 0; b < bytesRead; b++){
			 temp.put(buffer.get());
		 }
		 try {
		 if(DEBUG_first){
			ByteBuffer welcomeTemp = (ByteBuffer.wrap(WELCOME));
			welcomeTemp.clear();
			bytes = channel.write(welcomeTemp);
			 System.out.println(Integer.toString(bytes) + new String(welcomeTemp.array()));
			 DEBUG_first = false;
		 }else{
			 for(int client = 0; client < clients.size(); client++){
				 
				 SocketChannel c = clients.get(client).getChannel();
				 temp.clear();
				 int bytesWritten = 0;
				 if( c.isConnected() == true){
				 bytesWritten = c.write(temp);
				 }
				 System.out.println("Sent: " + Integer.toString(bytesWritten) + " to " + c.socket().getInetAddress());
			 }
		 }
		 channel.register(selector, SelectionKey.OP_READ);
		 } catch (IOException e) {
			 e.printStackTrace();
		 }
		
	}
}
