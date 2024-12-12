package uulm.in.vs.ex4;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import uulm.in.vs.ex4.ChatGrpc.ChatBlockingStub;
import uulm.in.vs.ex4.ChatGrpc.ChatStub;


/**
 * Represents a client for interacting with a chat service using gRPC.
 * This client supports synchronous and asynchronous operations like login, logout,
 * sending messages, and listing users.
 */
public class ChatClient {

    /**
     * A blocking stub to perform synchronous RPC calls to the Chat service.
     * Used for executing RPC requests that require a synchronous response.
     */
    private final ChatBlockingStub blockingStub;
    /**
     * An asynchronous stub for interacting with the Chat service.
     * Used to perform non-blocking RPC calls.
     */
    private final ChatStub asyncStub;

    /**
     * Represents the unique identifier for the user's session.
     * Used to track and validate active user sessions.
     */
    private String sessionID;
    /**
     * Stores the username of the client.
     */
    private String username;

    /**
     * Stream observer for handling client messages in the chat.
     */
    private StreamObserver<ClientMessage> clientMessageStreamObserver;
    /**
     * StreamObserver to handle streaming of ChatMessages.
     */
    private StreamObserver<ChatMessages> chatMessagesStreamObserver;


    /**
     * Creates a new ChatClient instance and initializes communication with the server.
     *
     * @param host the server hostname or IP address.
     * @param port the server port number.
     */
    public ChatClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = ChatGrpc.newBlockingStub(channel);
        this.asyncStub = ChatGrpc.newStub(channel);

        this.clientMessageStreamObserver = null;
        this.chatMessagesStreamObserver = null;
    }

    /**
     * Authenticates the user and initializes the session.
     *
     * @param username the username for login.
     * @return {@code true} if login is successful, otherwise {@code false}.
     */
    public boolean login(String username) {
        LoginRequest request = LoginRequest.newBuilder().setUsername(username).build();
        LoginResponse response = blockingStub.login(request);
        if (response.getStatus() == StatusCode.OK) {
            this.username = username;
            this.sessionID = response.getSessionID();
            createChatStream();

            return true;
        } else {
            return false;
        }
    }

    /**
     * Logs the user out of the current session.
     *
     * @return {@code true} if logout is successful, otherwise {@code false}.
     */
    public boolean logout() {
        if (sessionID == null) {
            return false;
        }

        LogoutRequest request = LogoutRequest.newBuilder().setSessionID(sessionID).setUsername(username).build();
        LogoutResponse response = blockingStub.logout(request);
        if (response.getStatus() == StatusCode.OK) {
            sessionID = null;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Retrieves and displays the list of users from the server.
     */
    public void listUsers() {
        GetUsersMessage request = GetUsersMessage.newBuilder().setUsername(username).setSessionID(sessionID).build();
        UserInfoMessage response = blockingStub.listUsers(request);
        for (String user : response.getUsersList()) {
            System.out.println(user);
        }
    }

    /**
     * Sends a message to the connected chat server.
     *
     * @param message the content of the message to be sent.
     */
    public void sendMessage(String message) {
        ClientMessage request = ClientMessage.newBuilder().setUsername(username).setSessionID(sessionID).setMessage(message).build();
        System.out.println("Message send -> [" + username + "]: " + message);
        if (clientMessageStreamObserver != null) clientMessageStreamObserver.onNext(request);
    }

    /**
     * Initializes and sets up the chat stream observer for handling incoming chat messages.
     * Also starts the client-side message stream for sending messages.
     */
    private void createChatStream() {
        chatMessagesStreamObserver = new StreamObserver<ChatMessages>() {
            @Override
            public void onNext(ChatMessages value) {
                System.out.println(username + " -> [" + value.getUsername() + "]: " + value.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Chat Stream Error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("Chat stream completed.");
            }
        };

        clientMessageStreamObserver = asyncStub.chatStream(chatMessagesStreamObserver);
        sendMessage("Joined chat as " + username);
    }

    /**
     * Entry point of the application.
     *
     * @param args command-line arguments passed to the program.
     */
    public static void main(String[] args) {
        ChatClient client1 = new ChatClient("localhost", 5555);
        ChatClient client2 = new ChatClient("localhost", 5555);

        if (!client1.login("user1")) {
            System.err.println("Failed to login as user1");
            return;
        }
        if (!client2.login("user2")) {
            System.err.println("Failed to login as user2");
            return;
        }

        client1.sendMessage("Hello World!");
        client2.sendMessage("Hello World!");

        client1.listUsers();
        client2.listUsers();

        client1.logout();
        client2.logout();
    }
}
