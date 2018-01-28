package main;
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
import java.util.Iterator;

public class ToeServ implements Runnable{

    	//private ProcessorThread processor;
    	
    	private ServerSocketChannel socketChannel;
    	
    	public ArrayList<Socket>clients = new ArrayList<Socket>();
    	
    	private Selector selector;
    	
    	ByteBuffer buffer = ByteBuffer.allocate(1024);
    	
    	private final byte[] WELCOME = "MESSAGE:Welcome From The Server!".getBytes(Charset.forName("UTF-8"));;
    	
    	private boolean first = true;
    	
    	private int bytesRead = 0;
    	
        public ToeServ(){
        	
        	try{        		
        		socketChannel = ServerSocketChannel.open();
        		socketChannel.configureBlocking(false);
        		socketChannel.socket().bind( new InetSocketAddress("0.0.0.0", 40052) );
        		selector = SelectorProvider.provider().openSelector();
        		socketChannel.register(selector, SelectionKey.OP_ACCEPT);
        		
        	}catch(IOException e){       		
        		e.printStackTrace();    
        	}
        	
        	//process = new ProcessThread(this);
        	//process.start();
        }
        
        public ArrayList<Socket> getClients(){return clients;}
       
        
        public void run(){
			try {

				 while(true)
				 {		 
					 //process.processData();
					 
					 selector.select();
					 
					 //Must User selectedKeys() or exception will be thrown
					 //when you try to modify any key (removing them)
					 Iterator i = selector.selectedKeys().iterator();
					 
					 while(i.hasNext()){
						 
						 SelectionKey key = (SelectionKey)i.next();
						 i.remove();
						 
						 if (!key.isValid()) {
					            continue;
					     }
						 
						 if(key.isAcceptable()){
							 accept(key);
						 }else if(key.isReadable()){
							 read(key);
						 }else if(key.isWritable()){
								 write(key);
						 }
						 
					 }
					 
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			try {
				socketChannel.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        }

        private void accept(SelectionKey key){
        	try{
			 //Accept Connection.Add Client To Array.Make Connection NonBlocking
			 ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
			 SocketChannel channel = serverChannel.accept();
			 
			 if(!clients.contains(channel.socket())){
				 clients.add(channel.socket());
				 first = true;
			 } else { first = false; }
			 
			 
			 channel.configureBlocking(false);
			 System.out.println("Connection Accepted at: " + channel.socket().getInetAddress().toString());
			 
			 channel.register(selector, SelectionKey.OP_WRITE);
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
        }
        
        private void read(SelectionKey key){
        	 SocketChannel channel = (SocketChannel)key.channel();
			 buffer.clear();
			 
			 // number of bytes for read
			 int bytes = 0;
			 // gets bytes from channel read
			 try{
					 // sets bytes to byte variable with greater scope
					 bytes = channel.read(buffer);
					 bytesRead = bytes;
					 System.out.println("Reading in " + Integer.toString(bytesRead) + " bytes: " + new String(buffer.array()));
				 
        	} catch (IOException e){
    			key.cancel();
    			try {
    			channel.close();
    			} catch (IOException e1) { 
    				e1.printStackTrace();
    			}
    			return;
    		}
    		
    		if(bytes == -1){
    			try {
    				clients.remove(channel.socket());
    				key.channel().close();
    				
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    			System.out.println("Connection Closed");
    			key.cancel();
    			return;
    		}
			 System.out.println("Received: " + new String(buffer.array()) );
				// sets channel to Operation_Write state
			 try {
				channel.register(selector, SelectionKey.OP_WRITE);
			} catch (ClosedChannelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			 
			 
			 /*if(buffer.remaining() != 0){
				 processor.addData(buffer.array());
				 channel.register(selector, SelectionKey.OP_READ);
			 }*/
        }
        
        private void write(SelectionKey key){
        	try{
        		SocketChannel channel = (SocketChannel)key.channel();
   			 	int bytes;
        	 if(!first){
    			 ByteBuffer temp = ByteBuffer.allocate(bytesRead);
    			 buffer.rewind();
    			 
    			 for(int b = 0; b < bytesRead; b++){
    				 temp.put(buffer.get());
    			 }
        		 
				 for(int client = 0; client < clients.size(); client++){
					 
					 SocketChannel c = clients.get(client).getChannel();
					 temp.rewind();
					 int bytesWritten = c.write(temp);
					 
					 System.out.println("Sent " + Integer.toString(bytesWritten) + " bytes:" + c.socket().getInetAddress());
				 }
        		 
        	 } else {
        		 	bytes = channel.write(ByteBuffer.wrap(WELCOME));
        		 	System.out.println(Integer.toString(bytes));
        		 	first = false;
        		 	System.out.println("Sent Welcome Message: " + channel.socket().getInetAddress());
			 }
			 
			 buffer.flip();
			 channel.register(selector, SelectionKey.OP_READ);

        	} catch (IOException e){
        		e.printStackTrace();
        	}
        }
        
    }//End Of ClientThread





