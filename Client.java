import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
	Socket connSocket;
	MessageThread messageThread;
	PrintWriter out;
	BufferedReader in;
	boolean connected = true;

	public Client(String serverAddr, int port) {
		try {
			connSocket = new Socket(serverAddr, port);
			System.out.println("Connected!");

			out = new PrintWriter(connSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
			messageThread = new MessageThread(in);
			messageThread.start();
			Runtime.getRuntime().addShutdownHook(new Thread() {
			    public void run() { 
			    	out.println("logout");
			    }
			});
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				String input;
				if ((input = stdIn.readLine()) != null) {
					out.println(input);
				}
			}
		} catch (UnknownHostException e) {
			System.out.println("ERROR: Cannot connect to " + serverAddr);
		} catch (IOException e) {
			System.out.println("ERROR: Cannot connect to " + serverAddr + " at port " + port);
		}
	}

	private void closeConn()
	{
		try {
			if (in != null)
				in.close();
			if (out != null)
				out.close();
			if (connSocket != null)
				connSocket.close();
		} catch (IOException e) {  
            		e.printStackTrace();  
            	}
		
	}

	public static void main(String[] args) {
		String serverAddr = args[0];
		int port = Integer.parseInt(args[1]);

		Client client = new Client(serverAddr, port);
	}

	private class MessageThread extends Thread {
		BufferedReader in;
		public MessageThread(BufferedReader in) {
			this.in = in;
		}

		public void run() {
			try {
				String msg;
				while ((msg = in.readLine()) != null) {
					if (msg.equals("TIME_OUT")) {
						out.println("logout");
						continue;
					} 
					if (msg.equals("SHUT_DOWN")) {
						closeConn();
						System.exit(0);
					}
					System.out.println(msg);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

}
