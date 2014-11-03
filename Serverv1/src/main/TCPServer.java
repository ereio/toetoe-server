package main;
public class TCPServer{
	
	public static void main(String[] args){
		new Thread(new Server()).start();
	}
	
}