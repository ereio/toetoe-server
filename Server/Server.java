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
import java.util.Iterator;

public class Server implements Runnable{

    	//private ProcessorThread processor;
    	
    	private ServerSocketChannel socketChannel;
    	
    	public ArrayList<Socket>clients = new ArrayList<Socket>();
    	
    	private Selector selector;
    	
    	ByteBuffer buffer = ByteBuffer.allocate(124);
    	
    	private final byte[] WELCOME = "Welcome From The Server!".getBytes(Charset.forName("UTF-8"));;
    	
    	private boolean first = true;
    	
    	private int bytesRead = 0;
    	
        public Server(){
        	
        	try{        		
        		socketChannel = ServerSocketChannel.open();
        		socketChannel.configureBlocking(false);
        		socketChannel.socket().bind( new InetSocketAddress("0.0.0.0", 2768) );
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
							 //Accept Connection.Add Client To Array.Make Connection NonBlocking
							 ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
							 SocketChannel channel = serverChannel.accept();
							 
							 clients.add(channel.socket());
							 
							 channel.configureBlocking(false);
							 System.out.println("Connection Accepted at: " + channel.socket().getInetAddress().toString());
							 
							 channel.register(selector, SelectionKey.OP_WRITE);
						 }else if(key.isReadable()){
							 
							 SocketChannel channel = (SocketChannel)key.channel();
							 
							 buffer.clear();
							 
							 int bytes;
							 
							 while ( (bytes = channel.read(buffer)) != 0){
								 bytesRead = bytes;
								 System.out.println(Integer.toString(bytesRead));
								 if(bytesRead == -1){
									 System.out.println("Error Has Occured In Reading");
								 }
							 }
							 
							 
							 System.out.println("Received: " + new String(buffer.array()) );

							 channel.register(selector, SelectionKey.OP_WRITE);
							 /*if(buffer.remaining() != 0){
								 processor.addData(buffer.array());
								 channel.register(selector, SelectionKey.OP_READ);
							 }*/
						 
						 }else if(key.isWritable()){
							 
							 SocketChannel channel = (SocketChannel)key.channel();
							 
							 int bytes;
							 
							 ByteBuffer temp = ByteBuffer.allocate(bytesRead);
							 
							 buffer.rewind();
							 
							 for(int b = 0; b < bytesRead; b++){
								 temp.put(buffer.get());
							 }
							 
							 if(first){
								 bytes = channel.write(ByteBuffer.wrap(WELCOME));
								 System.out.println(Integer.toString(bytes));
								 first = false;
							 }else{
								 for(int client = 0; client < clients.size(); client++){
									 
									 SocketChannel c = clients.get(client).getChannel();
									 temp.rewind();
									 int bytesWritten = c.write(temp);
									 
									 System.out.println("Sent: " + Integer.toString(bytesWritten) + " to " + c.socket().getInetAddress());
								 }
							 }
							 channel.register(selector, SelectionKey.OP_READ);
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

    }//End Of ClientThread

