import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

class User {
	private String name;
	private String password;
	private PrintWriter writer = null;
	private Date loginTime = null;
	private Date logoutTime = null; 
	private Date activeTime = null;
	private Date blockTime = null;
	
	public String getName() {
		return name;
	}
	public void setNamePass(String name, String password) {
		this.name = name;
		this.password = password;
	}
	public String getPassword() {
		return password;
	}
	public PrintWriter getWriter() {
		return writer;
	}
	public void setWriter(PrintWriter writer) {
		this.writer = writer;
	}
	public Date getLoginTime() {
		return loginTime;
	}
	public void setLoginTime(Date loginTime) {
		this.loginTime = loginTime;
	}
	public Date getLogoutTime() {
		return logoutTime;
	}
	public void setLogoutTime(Date logoutTime) {
		this.logoutTime = logoutTime;
	}
	public Date getActiveTime() {
		return activeTime;
	}
	public void setActiveTime(Date activeTime) {
		this.activeTime = activeTime;
	}
	public Date getBlockTime() {
		return blockTime;
	}
	public void setBlockTime(Date blockTime) {
		this.blockTime = blockTime;
	}
}

public class Server {
	private static final int LAST_HOUR = 60 * 60; /* seconds */
	private static final int TIME_OUT = 30 * 60; /* seconds */
	private static final int BLOCK_TIME = 60; /* seconds */
	private HashMap<String,User> users = new HashMap<>();
	
	public Server(int port){

		/* Read from user_pass.txt */
		String file_path = System.getProperty("user.dir");
		Path file = FileSystems.getDefault().getPath(file_path, "user_pass.txt");
		try {
			byte[] bytes = Files.readAllBytes(file);
			String line = new String(bytes, "UTF-8");

			String[] tmp = line.split("\n");
			for (int i = 0; i < tmp.length; i++) {
				String[] parts = tmp[i].split(" ");
				User user = new User();
				user.setNamePass(parts[0], parts[1]);
				users.put(parts[0], user);
			}

			ServerSocket serverSocket;
			serverSocket = new ServerSocket(port);
			System.out.println("Server is running");
		
			try {
				while(true) {
					new ServerThread(serverSocket.accept()).start();
				}
			} finally {
				serverSocket.close();
			}

		} catch (IOException e) {
			System.out.println("A conn is closed");
		}
	}

	private class ServerThread extends Thread {
		private Socket connSocket;
		private BufferedReader in;
		private PrintWriter out;
		private String username;
		private int attempt = 3;

		public ServerThread(Socket socket) {
			this.connSocket = socket;
		}
		
		private void logoutHandler() {
			users.get(username).setLogoutTime(new Date());
			users.get(username).setWriter(null);
			System.out.println(username + " logged out");
		}

		private void printError(String msg) {
			out.println(">>Error: " + msg);
		}
		
		private void restartThread() {
			ServerThread.this.stop();
			new ServerThread(connSocket).start();
		}

		private void checkActive(){
			Timer timer = new Timer();

			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					long diff = (new Date()).getTime() - users.get(username).getActiveTime().getTime();
					if (diff > TIME_OUT * 1000) {
						out.println("TIME_OUT");
						logoutHandler();
						restartThread();
					}	
				}
			},  TIME_OUT * 1000);
		}

		private void shutDown() {
			try {
				if (in != null)
					in.close();
				if (out != null)
					out.close();
				if (connSocket != null)
					connSocket.close();

				System.out.println("Connection with " + connSocket.getInetAddress() + " is closed");
				ServerThread.this.stop();
			} catch (IOException e) {  
	            		e.printStackTrace();  
	            	}
		}

		private boolean loginHandler() {
//			HashMap<String, Integer> loginAttempts = new HashMap<>();
			try {
				while (true) {
					out.println(">>Username");
					String tmp = in.readLine();
					if (tmp.equals("SHUT_DOWN"))
						shutDown();
					else
						username = tmp;

					/* check block time */
					Date blockTime = users.get(username).getBlockTime();
					if (blockTime != null) {
						long diff = new Date().getTime() - blockTime.getTime();
						if (diff < BLOCK_TIME * 1000) {
							printError(username + " is blocked, please try " + (BLOCK_TIME - diff/1000) + " seconds later");
							continue;
						} else {
							users.get(username).setBlockTime(null);
						}
					}

					if (!users.containsKey(username)) {
						printError("no such user");
					} else if (users.get(username).getWriter() != null) {
						printError(username + " is already logged in");
						break;
					} else {
						out.println(">>Password:");
					}
					
					/* detect 3 consecutive wrong psw */
					while (attempt > 0) {
						tmp = in.readLine();
						if (tmp.equals("SHUT_DOWN")) {
							logoutHandler();
							shutDown();
						}

						if (!tmp.equals(users.get(username).getPassword())) {
							attempt--;

							if (attempt < 1)
								printError("3 consecutive errors, you are blocked for "+BLOCK_TIME+" seconds");
							else 
								printError("wrong password, you have " + attempt + " more chances");
						} else {
							out.println(">>Welcome, " + username);
							users.get(username).setLoginTime(new Date());
							users.get(username).setLogoutTime(null);
							users.get(username).setWriter(out);
							users.get(username).setActiveTime(new Date());
							return true;
						}
					}
					/* block the user */
					users.get(username).setBlockTime(new Date());
					System.out.println(username + " is blocked for " + BLOCK_TIME + " seconds");
				}
			} catch (IOException e) {
				System.out.println("Error in loginHandler");
			}
			return false;
		}
		public void run() {
			try {
				in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
				out = new PrintWriter(connSocket.getOutputStream(), true);
				loginHandler();
				checkActive();
				/* process cmd */
				String line;
				while (true) {
					line = in.readLine();
					if (line.equals("SHUT_DOWN")) {
						logoutHandler();
						shutDown();
					} 

					if (line != null){
						users.get(username).setActiveTime(new Date());
						checkActive();
						String[] parts = line.split(" ", 2);
						String cmd = parts[0];
						
						if (cmd.equals("whoelse")) {
							for (User user: users.values()) {
								if (user.getName().equals(username))
									continue;
								if (user.getWriter() != null)
									out.println(user.getName());
							}
						} else if (cmd.equals("wholasthr")) {
							Date currentTime = new Date();
							Date logoutTime;
							for (User user: users.values()) {
								if (user.getName().equals(username))
									continue;
								
								logoutTime = users.get(username).getLogoutTime();
								if (logoutTime != null) {
									long diff = currentTime.getTime() - logoutTime.getTime();
									long oneHourInMillisec = LAST_HOUR * 1000;
									if (diff > oneHourInMillisec)
										continue;
								}
								
								if (logoutTime == null && user.getLoginTime() == null)
									continue;

								out.println(user.getName());
							}
						} else if (cmd.equals("message")) {
							/* input contains only "message" */
							if (parts.length < 2) {
								printError("invalid command");
								break;
							}
							String[] tmp = parts[1].split(" ", 2);
							/* input contains only 2 args*/
							if (tmp.length < 2) {
								printError("invalid command");
								break;
							}

							String receiver = tmp[0];
							if (!users.containsKey(receiver))
								printError("user \"" + receiver + "\" does not exist");
							else {
								String msg = username + ": " + tmp[1];
								users.get(receiver).getWriter().println(">>" + msg);
							}
						} else if (cmd.equals("broadcast")) {
							if (parts.length < 2) {
								printError("invalid command");
								break;
							}
							for (User user: users.values()) {
								if (user.getName().equals(username))
									continue;
								if (user.getWriter() == null)
									continue;
								String msg = username + ": " + parts[1];
								user.getWriter().println(">>" + msg);
							}
						} else if (cmd.equals("logout")) {
							logoutHandler();
						} else {
							printError("command not exist");
						} // end switch
					} // end if(line != null)
				}// end while
			} catch (IOException e) {
				System.out.println("Connection with " + connSocket.getInetAddress() + " is closed");
				shutDown();
		    	}
		}
	}
	

	public static void main(String[] args) {
		int port = Integer.parseInt(args[0]);
		Server server = new Server(port);
	}

}
