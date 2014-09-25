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
}

public class Server {
	private enum Command {WHOELSE, WHOLASTHR, BROADCAST, MESSAGE, LOGOUT};
	private static final int LAST_HOUR = 60 * 60; /* seconds */
	private static final int TIME_OUT = 10; /* seconds */
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

		} catch (IOException e) {
			e.printStackTrace();
		}

		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Server is running");
		
			try {
				while(true)
					new ServerThread(serverSocket.accept()).start();
			} finally {
				serverSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		

	}

	private class ServerThread extends Thread {
		private Socket connSocket;
		private BufferedReader in;
		private PrintWriter out;
		private String username;
		// private Integer attempt = 0;

		public ServerThread(Socket socket) {
			this.connSocket = socket;
		}
		
		private void logoutHandler() {
			users.get(username).setLogoutTime(new Date());
			users.get(username).setWriter(null);
			System.out.println(username + " logged out");
			
		}

		private void printError(String msg) 
		{
			out.println(">>Error: " + msg);
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
						ServerThread.this.stop();
						new ServerThread(connSocket).start();
						
					}	
				}
			},  TIME_OUT * 1000);
		}

		private boolean loginHandler() {
			try {
				while (true) {
					out.println(">>Username");
					username = in.readLine();

					if (!users.containsKey(username)) {
						printError("no such user");
					}
					else if (users.get(username).getWriter() != null) {
						printError(username + " is already logged in");
						break;
					}
					else {
						out.println(">>Password:");
					}
					
					if (!in.readLine().equals(users.get(username).getPassword())) {
						printError("wrong password");
					}
					else {
						out.println("Welcome, " + username);
						users.get(username).setLoginTime(new Date());
						users.get(username).setLogoutTime(null);
						users.get(username).setWriter(out);
						users.get(username).setActiveTime(new Date());
						return true;
					}
					
				}
			} catch (IOException e) {
		                System.out.println(e);
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
				while ((line = in.readLine()) != null) {
					users.get(username).setActiveTime(new Date());
					checkActive();
					String[] parts = line.split(" ", 2);
					Command cmd = Enum.valueOf(Command.class, parts[0].toUpperCase());
					switch (cmd) {
					case WHOELSE:
						for (User user: users.values()) {
							if (user.getName().equals(username))
								continue;
							if (user.getWriter() != null)
								out.println(user.getName());
						}
						break;
					case WHOLASTHR:
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
						break;
					case MESSAGE:
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
							users.get(receiver).getWriter().println(msg);
						}
						break;
					case BROADCAST:
						if (parts.length < 2) {
							printError("invalid command");
							break;
						}
						for (User user: users.values()) {
							if (user.getName().equals(username))
								continue;
							String msg = username + ": " + parts[1];
							user.getWriter().println(msg);
						}
						break;
					case LOGOUT:
						logoutHandler();
						
						in.close();
						out.close();
						connSocket.close();
						
						break;
					default:
						printError("command not exist");
						break;

					}
				}
			} catch (IOException e) {
		                System.out.println(e);
		    } 
		}
	}
	

	public static void main(String[] args) {
		int port = Integer.parseInt(args[0]);
		Server server = new Server(port);

		// BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));
		// String input;
		// try {
		// 	input = inputBuffer.readLine();
		// 	System.out.println(input);
		// } catch (IOException e) {
		// 	e.printStackTrace();
		// }
		
	}

}
