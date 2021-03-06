package Server;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;

public class ServerWorker extends Thread {
    private final Server server;
    private final Socket clientSocket;
    private String connectedUser;
    private InputStream inputStream;
    private OutputStream outputStream;
    private HashSet<String> groupSet = new HashSet<>();

    public ServerWorker(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            handleClientSocket();
        } catch (IOException e ) {
            e.printStackTrace();
        }
    }

    public void handleClientSocket() throws IOException {
        this.inputStream = clientSocket.getInputStream();
        this.outputStream = clientSocket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String buff;
        while((buff = reader.readLine()) != null) {
            String[] tokens = StringUtils.split(buff, null, 3);
            if(tokens != null && tokens.length > 0) {
                String cmd = tokens[0];
                if(cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("logout")) {
                    handleLogout();
                    break;
                }
                else if(cmd.equalsIgnoreCase("signup")) {
                    handleSignUp(tokens);
                }
                else if(cmd.equalsIgnoreCase("login")) {
                    handleLogin(tokens);
                }
                else if(cmd.equalsIgnoreCase("msg")) {
                    handleMessage(tokens);
                }
                else if(cmd.equalsIgnoreCase("join")) {
                    handleJoinGroup(tokens);
                }
                else if(cmd.equalsIgnoreCase("leave")) {
                    handleLeaveGroup(tokens);
                }
                else if(cmd.equalsIgnoreCase("help")) {
                    outputStream.write(("The accepted commands are:\n" +
                                        "signup <username> <password>\n" +
                                        "login <username> <password>\n" +
                                        "logout\n" +
                                        "msg <username> <message_body>\n" +
                                        "join <group_name>\n" +
                                        "leave <group_name>\n" +
                                        "exit\n" +
                                        "help\n"
                                        ).getBytes());
                }
                else {
                    outputStream.write((cmd + " is not a command\nTry >help for the list of commands\n").getBytes());
                }
            }
        }

        clientSocket.close();
    }

    private void handleSignUp(String[] tokens) throws IOException {
        UserDatabase database = server.getDatabase();
        if(tokens.length > 2 && !database.userExists(tokens[1])) {
            database.addEntry(tokens[1], tokens[2]);
        }
        else {
            outputStream.write(("SignUp failed\n").getBytes());
        }
    }

    private void handleLeaveGroup(String[] tokens) throws IOException {
        if(tokens.length > 1) {
            String groupName = tokens[1];
            groupSet.remove(groupName);
        }
        else {
            outputStream.write(("Leave group failed\n").getBytes());
        }
    }

    private void handleJoinGroup(String[] tokens) throws IOException {
        if(tokens.length > 1) {
            String groupName = tokens[1];
            groupSet.add(groupName);
        }
        else {
            outputStream.write(("Join group failed\n").getBytes());
        }
    }

    private void handleMessage(String[] tokens) throws IOException {
        if(tokens.length > 2) {
            String sendTo = tokens[1];

            String body = tokens[2];
            ArrayList<ServerWorker> workersList = server.getWorkers();
            if(sendTo.charAt(0) == '#') {
                for (ServerWorker sw : workersList) {
                    if (groupSet.contains(sendTo)) {
                        sw.send("msg " + connectedUser + " in " + sendTo + " " + body + "\n");
                    }
                }
            }
            else {
                for (ServerWorker sw : workersList) {
                    if (sendTo.equalsIgnoreCase(sw.getConnectedUser())) {
                        System.out.println("msg " + connectedUser + " " + body + "\n");
                        sw.send("msg " + connectedUser + " " + body + "\n");
                    }
                }
            }
        }
        else {
            outputStream.write(("Sending message failed\n").getBytes());
        }
    }

    private void handleLogout() throws IOException {
        server.removeWorker(this);
        ArrayList<ServerWorker> workersList = server.getWorkers();
        for(ServerWorker sw : workersList) {
            if((sw.getConnectedUser() != null) && !sw.getConnectedUser().equals(connectedUser)) {
                sw.send("offline " + connectedUser + "\n");
            }
        }
        clientSocket.close();
    }

    private void handleLogin(String[] tokens) throws IOException {
        if(tokens.length == 3) {
            String username = tokens[1];
            String password = tokens[2];
            ArrayList<ServerWorker> workersList = server.getWorkers();
            UserDatabase database = server.getDatabase();

            if(username.equals("admin") && password.equals("admin")) {
                atConnection(username, workersList);
            }
            else if(username.equals("guest") && password.equals("guest")) {
                atConnection(username, workersList);
            }
            else if(database.checkLogin(username, password)) {
                atConnection(username, workersList);
            }
            else {
                outputStream.write(("Login failed\n").getBytes());
            }
        }
    }

    private void atConnection(String username, ArrayList<ServerWorker> workersList) throws IOException {
        outputStream.write(("ok login" + "\n").getBytes());
        this.connectedUser = username;
        for(ServerWorker sw : workersList) {
            if( (sw.getConnectedUser() != null) && !sw.getConnectedUser().equals(connectedUser)) {
                sw.send("online " + connectedUser + "\n");
                send("online " + sw.getConnectedUser() + "\n");
            }
        }
    }

    private void send(String message) throws IOException {
        if(connectedUser != null) {
            outputStream.write(message.getBytes());
        }
    }

    public String getConnectedUser() {
        return connectedUser;
    }
}
