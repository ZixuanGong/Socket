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
import java.util.ArrayList;

class User {
	private String name;
	private String password;
	private PrintWriter writer = null;
	private Date loginTime = null;
	private Date logoutTime = null; 
	
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
}

public class Server {
	private enum Command {WHOELSE, WHOLASTHR, BROADCAST, MESSAGE, LOGOUT};

	private HashMap<String,User> users = new HashMap<>();
	private static final long LAST_HOUR = (long) 60; /* minutes */
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

		private void printError(String msg) 
		{
			out.println(">>Error: " + msg);
		}

		public void run() {
			try {
				in = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
				out = new PrintWriter(connSocket.getOutputStream(), true);

				/* Log in */
				while (true) {
					out.println(">>Username");
					username = in.readLine();

					if (!users.containsKey(username)) {
						printError("no such user");
					}
					else if (users.get(username).getWriter() != null) {
						printError(username + "is already logged in");
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
						break;
					}
				}
				
				String line;
				while ((line = in.readLine()) != null) {
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
								long oneHourInMillisec = LAST_HOUR * 60 * 1000;
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
						users.get(username).setLogoutTime(new Date());
						users.get(username).setWriter(null);
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
