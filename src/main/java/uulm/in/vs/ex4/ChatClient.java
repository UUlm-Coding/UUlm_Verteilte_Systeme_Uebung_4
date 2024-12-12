package uulm.in.vs.ex4;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import uulm.in.vs.ex4.ChatGrpc.ChatBlockingStub;
import uulm.in.vs.ex4.ChatGrpc.ChatStub;

public class ChatClient {
    private final ChatBlockingStub blockingStub;
    private final ChatStub asyncStub;

    private String sessionID;
    private String username;

    private StreamObserver<ClientMessage> clientMessageStreamObserver;
    private StreamObserver<ChatMessages> chatMessagesStreamObserver;


    public ChatClient(String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = ChatGrpc.newBlockingStub(channel);
        this.asyncStub = ChatGrpc.newStub(channel);

        this.clientMessageStreamObserver = null;
        this.chatMessagesStreamObserver = null;
    }

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

    public boolean logout() {
        if (sessionID == null) {
            return false;
        }

        LogoutRequest request = LogoutRequest.newBuilder().setSessionID(sessionID).build();
        LogoutResponse response = blockingStub.logout(request);
        if (response.getStatus() == StatusCode.OK) {
            sessionID = null;
            return true;
        } else {
            return false;
        }
    }

    public void listUsers() {
        GetUsersMessage request = GetUsersMessage.newBuilder().build();
        UserInfoMessage response = blockingStub.listUsers(request);
        for (String user : response.getUsersList()) {
            System.out.println(user);
        }
    }

    public void sendMessage(String message) {
        ClientMessage request = ClientMessage.newBuilder().setUsername(username).setSessionID(sessionID).setMessage(message).build();
        clientMessageStreamObserver.onNext(request);
    }

    private void createChatStream() {
        chatMessagesStreamObserver = new StreamObserver<ChatMessages>() {
            @Override
            public void onNext(ChatMessages value) {
                System.out.println("[" + value.getUsername() + "]: " + value.getMessage());
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
    }

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
