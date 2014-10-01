import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
	private String blockIP = null;
	private ArrayList<String> offlineMsg = null;
	
	public void addOfflineMsg(String msg) {
		offlineMsg.add(msg);
	}
	public ArrayList<String> getOfflineMsg() {
		return offlineMsg;
	}
	public void initOfflineMsg(ArrayList<String> offlineMsg) {
		this.offlineMsg = offlineMsg;
	}
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
	public String getBlockIP() {
		return blockIP;
	}
	public void setBlockIP(String blockIP) {
		this.blockIP = blockIP;
	}
}

public class Server {
	private static final int LAST_HOUR = 10; /* seconds */
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
				user.initOfflineMsg(new ArrayList<String>());
				users.put(parts[0], user);
			}

			ServerSocket serverSocket;
			serverSocket = new ServerSocket(port);
			System.out.println("Server is running");

			Runtime.getRuntime().addShutdownHook(new Thread() {
				    public void run() {
				    	for (User user: users.values()) {
				    		if (user.getWriter() != null) {
					    		user.getWriter().println(">>Server is closed");
						    	user.getWriter().println("SHUT_DOWN");
				    		}
				    	}
				    }
			});
		
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
		private String IpAddr;
		private BufferedReader in;
		private PrintWriter out;
		private String username;
		private int attempt;

		public ServerThread(Socket socket) {
			this.connSocket = socket;
			this.IpAddr = connSocket.getRemoteSocketAddress().toString();
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
			new ServerThread(connSocket).start();
			ServerThread.this.stop();
		}

		private void checkActive(){
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					long diff = (new Date()).getTime() - users.get(username).getActiveTime().getTime();
					if (diff > TIME_OUT * 1000) {
						out.println(">>SERVER: Time out, please log in again");
						out.println("TIME_OUT");
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

		private void loginHandler() {
			try {
				while (true) {
					out.print(">>Username:");
					username = in.readLine();

					/* check if the user is blocked form this IP */
					Date blockTime = users.get(username).getBlockTime();
					String blockIp = users.get(username).getBlockIP();
					/* current IP has been blocked before */
					if (blockTime != null && blockIp != null && blockIp.equals(IpAddr)) {
						long diff = new Date().getTime() - blockTime.getTime();
						if (diff < BLOCK_TIME * 1000) {
							printError(username + " is blocked from this IP, please try " + (BLOCK_TIME - diff/1000) + " seconds later");
							continue;
						} else {
							users.get(username).setBlockTime(null);
							users.get(username).setBlockIP(null);
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
					attempt = 3;
					while (attempt > 0) {
						String tmp = in.readLine();
						if (!tmp.equals(users.get(username).getPassword())) {
							attempt--;

							if (attempt < 1) {
								printError("3 consecutive errors, this IP will be blocked for "+BLOCK_TIME+" seconds");
								/* block the user from this IP*/
								users.get(username).setBlockTime(new Date());
								users.get(username).setBlockIP(IpAddr);
								System.out.println(username + " is blocked from " + IpAddr + " for " + BLOCK_TIME + " seconds");
							}
							else if (attempt == 1)
								printError("wrong password, you have 1 more chance");
							else
								printError("wrong password, you have " + attempt + " more chances");	
						} else {
							out.println(">>SERVER: Welcome, " + username);
							users.get(username).setLoginTime(new Date());
							users.get(username).setLogoutTime(null);
							users.get(username).setWriter(out);
							users.get(username).setActiveTime(new Date());
							printOfflineMsg(users.get(username));
							return;
						}
					}
				}
			} catch (IOException e) {
				System.out.println("Error in loginHandler");
			} catch (NullPointerException e) {
				shutDown();
			}
		}
		
		private void printOfflineMsg(User user) {
			for (String msg: user.getOfflineMsg())
				user.getWriter().println(">>" + msg);
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
					if (line != null){
						if (line.equals("logout")){
							logoutHandler();
							restartThread();
							break;
						}

						users.get(username).setActiveTime(new Date());
						checkActive();
						String[] parts = line.split(" ", 2);
						String cmd = parts[0];
						
						if (cmd.equals("whoelse")) {
							for (User user: users.values()) {
								if (user.getName().equals(username))
									continue;
								if (user.getWriter() != null)
									out.println(">>" + user.getName());
							}
						} else if (cmd.equals("wholasthr")) {
							Date currentTime = new Date();
							Date logoutTime;
							long diff;
							long oneHourInMillisec = LAST_HOUR * 1000;
							for (User user: users.values()) {
								if (user.getName().equals(username))
									continue;
								
								logoutTime = user.getLogoutTime();
								// check offline users' logout time
								if (logoutTime != null) { //online users' logout time is null
									diff = currentTime.getTime() - logoutTime.getTime();
									if (diff > oneHourInMillisec)
										continue;
								}
								
								// check those who never gets online
								if (logoutTime == null && user.getLoginTime() == null)
									continue;

								out.println(">>" + user.getName());
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
							String msg = username + ": " + tmp[1];
							if (!users.containsKey(receiver))
								printError("user \"" + receiver + "\" does not exist");

							else if (receiver.equals(username))
								printError("you cannot send message to yourself");

							else if (users.get(receiver).getWriter() == null) { //receiver offline
								users.get(receiver).addOfflineMsg(msg);
								out.println(">>SERVER: " + receiver + " is offline");
							} else
								users.get(receiver).getWriter().println(">>" + msg);
						
						} else if (cmd.equals("broadcast")) {
							if (parts.length < 2) {
								printError("invalid command");
								break;
							}
							String msg = "[Broadcast] " + username + ": " + parts[1];
							for (User receiver: users.values()) {
								if (receiver.getName().equals(username))
									continue;
								if (receiver.getWriter() == null) {
									users.get(receiver.getName()).addOfflineMsg(msg);
									continue;
								}
								receiver.getWriter().println(">>" + msg);
							}
						} else {
							printError("command not exist");
						} // end switch
					} // end if(line != null)
				}// end while
			} catch (IOException e) {
				e.printStackTrace();
		    } catch (NullPointerException e) {
				shutDown();
		    }
		}
	}
	

	public static void main(String[] args) {
		int port = Integer.parseInt(args[0]);
		Server server = new Server(port);
	}

}
