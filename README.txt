a. A brief description of the code
The project is made up of 2 parts: Server.java and Client.java. The User class is located in Server.java. The Makefile is used for compiling. The constants LAST_HOUR, BLOCK_TIME, and TIME_OUT are all in seconds.The server should start running first, waiting for clients to connect to it. Whenever a connection is established, the server starts a new thread to handle it. 

Upon timeout, the user will be logged out, and asked for login info again. After giving 3 consecutive wrong passwords, the user will be blocked for BLOCK_TIME from this IP, but other users can still login from this IP. Ctrl+C is used to exit the program.

b. Details on development environment
The project has been developed and tested with java 1.7.

c. Instructions on how to run your code
Concurrent duplicated users are prohibited. <username>, <password> shall not contain spaces or newlines.

(First run server)
$make
$java Server 4444

(Then open another terminal to run client)
$java Client localhost 4444
>>Username:
google
>>Password:
hasglasses

d. Sample commands to invoke your code
Commands to run the code are just like the examples given in the homework. 

e. Additional functionalities
1. Clients can receive offline messages, including broadcast messages. When a user sends private messages to an offline user, the sender will be notified that the receiver is offline, and the receiver can see the offline messages upon login.
2. In the client terminal, if the user inputs ‘message <receiver> blabla’, but the <receiver> does not exist, the user would be notified. If the user inputs ‘message <receiver>’ without the message text, the server can also recognize this kind of command as invalid.
