package uulm.in.vs.ex4;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import uulm.in.vs.ex4.ChatGrpc.ChatImplBase;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a chat server that handles user sessions, chat functionalities, and user interactions
 * using gRPC services. Contains static inner classes and methods for managing login, logout, streaming
 * chat messages, listing users, and server initialization.
 */
public class ChatServer {
    /**
     * A thread-safe map used for storing user information in the chat server.
     * Keys and values represent usernames and their associated data.
     */
    private final static ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    /**
     * A thread-safe map that stores active chat streams associated with user identifiers.
     */
    private final static ConcurrentHashMap<String, StreamObserver<ChatMessages>> activeChatStreams = new ConcurrentHashMap<>();

    /**
     * ChatService provides various gRPC methods for user login, logout, chat messaging,
     * and listing registered users.
     */
    public static class ChatService extends ChatImplBase {
        /**
         * Handles the login request for a user. If the username is not already registered, a session ID is created
         * and returned in the response. If the username exists, the login is denied.
         *
         * @param request the login request containing the username
         * @param responseObserver the stream observer to send the login response
         */
        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
            String username = request.getUsername();
            if (users.containsKey(username)) {

                LoginResponse response = LoginResponse.newBuilder().setStatus(StatusCode.FAILED).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            String sessionID = UUID.randomUUID().toString();
            users.put(username, sessionID);
            LoginResponse response = LoginResponse.newBuilder().setStatus(StatusCode.OK).setSessionID(sessionID).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        /**
         * Handles the logout request for a user.
         *
         * @param request the logout request containing session information
         * @param responseObserver the stream observer used to send the logout response
         */
        @Override
        public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
            String sessionID = request.getSessionID();
            String username = request.getUsername();
            if (!sessionID.equals(users.get(username))) {
                LogoutResponse response = LogoutResponse.newBuilder().setStatus(StatusCode.FAILED).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            users.remove(username);
            LogoutResponse response = LogoutResponse.newBuilder().setStatus(StatusCode.OK).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        /**
         * Establishes a bidirectional streaming chat session between the client and server.
         *
         * @param responseObserver the stream observer used for sending chat messages to the client
         * @return a stream observer for handling client messages
         */
        @Override
        public StreamObserver<ClientMessage> chatStream(StreamObserver<ChatMessages> responseObserver) {
            return new StreamObserver<ClientMessage>() {
                String sessionID = null;
                String username = null;

                /**
                 * Processes the incoming client message and manages the streaming session.
                 *
                 * @param clientMessage the message sent by the client, containing the username, session ID, and message content
                 */
                @Override
                public void onNext(ClientMessage clientMessage) {
                    username = clientMessage.getUsername();
                    sessionID = users.get(username);

                    if (!clientMessage.getSessionID().equals(sessionID)) {
                        responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                        return;
                    }

                    if (!activeChatStreams.containsKey(sessionID)) {
                        activeChatStreams.put(sessionID, responseObserver);
                    }

                    ChatMessages chatMessages = ChatMessages.newBuilder().setUsername(username).setMessage(clientMessage.getMessage()).build();

                    for (StreamObserver<ChatMessages> observer : activeChatStreams.values()) {
                        if (observer == responseObserver) {
                            continue;
                        }
                        observer.onNext(chatMessages);
                    }
                }

                /**
                 * Handles an error that occurs during the streaming session.
                 *
                 * @param throwable the error that occurred
                 */
                @Override
                public void onError(Throwable throwable) {
                    if (sessionID != null) {
                        activeChatStreams.remove(sessionID);
                    }
                }

                /**
                 * Completes the chat streaming session by removing the session from active streams
                 * and notifying the client that the stream is complete.
                 */
                @Override
                public void onCompleted() {
                    if (sessionID != null) {
                        activeChatStreams.remove(sessionID);
                    }
                    responseObserver.onCompleted();
                }
            };
        }

        /**
         * Lists all users currently registered in the system.
         *
         * @param request the request containing user credentials for validation
         * @param responseObserver the stream observer to send the list of users
         */
        @Override
        public void listUsers(GetUsersMessage request, StreamObserver<UserInfoMessage> responseObserver) {
            if (!users.get(request.getUsername()).equals(request.getSessionID())) {
                responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
                return;
            }

            UserInfoMessage userInfoMessage = UserInfoMessage.newBuilder().addAllUsers(users.keySet()).build();
            responseObserver.onNext(userInfoMessage);
            responseObserver.onCompleted();
        }
    }

    /**
     * Entry point of the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            // Create and start the server
            Server server = ServerBuilder.forPort(5555)
                    .addService(new ChatService())
                    .build()
                    .start();

            // Add a hook to shut the server down if the program is terminated
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

            // Wait for the server to terminate
            server.awaitTermination();
        } catch (IOException | InterruptedException e) {
            System.err.println("Server terminated unexpectedly: " + e.getMessage());
        }
    }
}
