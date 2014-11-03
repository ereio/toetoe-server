package server_v2;

import java.io.IOException;


public class RunToeServer {
		public static void main(String[] args){
			try {
				ToeProcessor tpro = new ToeProcessor();
				new Thread(new ToeServer(tpro)).start();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
}
