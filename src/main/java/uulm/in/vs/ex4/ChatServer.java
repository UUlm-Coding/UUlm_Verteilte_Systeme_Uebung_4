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
 * ChatServer is a gRPC-based server implementation that provides chat functionalities
 * such as user login and logout. It handles client requests using gRPC mechanisms and
 * maintains a thread-safe user session state using a ConcurrentHashMap. The server listens
 * on a specified port and handles requests concurrently, making it suitable for real-time
 * chat applications.
 */
public class ChatServer {
    /**
     * A thread-safe map instance to maintain user information, where the key represents
     * the user identifier (e.g., username) and the value represents associated user data
     * or status within the chat application.
     * <p>
     * This map ensures concurrency safety for operations such as addition, removal, and
     * updates, making it suitable for multi-threaded scenarios typically encountered in
     * real-time chat server environments.
     */
    private final static ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<String, StreamObserver<ChatMessages>> activeChatStreams = new ConcurrentHashMap<>();

    /**
     * ChatService is a gRPC service implementation for handling chat functionalities including
     * user login and logout. It extends ChatGrpc.ChatImplBase and overrides its methods to provide
     * the core server-side logic for client requests.
     * <p>
     * The service maintains a mapping of usernames to unique session IDs to track active users
     * and ensure session consistency during the lifetime of the server.
     */
    public static class ChatService extends ChatImplBase {
        /**
         * Handles the login request for a user. If the username is available, the user is assigned
         * a unique session ID and the login response is sent. Otherwise, an error is returned
         * indicating that the username is already taken.
         *
         * @param request the login request, containing the username of the user attempting to log in
         * @param responseObserver the stream observer to send the login response or any errors
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
         * Handles the logout request for a user. If the user is found in the system, their session is
         * terminated and a logout response is sent. If the user is not found, an error is returned.
         *
         * @param request the logout request containing the username of the user attempting to log out
         * @param responseObserver the stream observer to send the logout response or any errors
         */
        @Override
        public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
            String sessionID = request.getSessionID();
            String username = users.get(sessionID);
            if (!users.contains(username) || !sessionID.equals(users.get(username))) {
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
         * Provides a bidirectional streaming method for handling chat messages between users. Clients send
         * their messages through this stream, and it broadcasts them to other active participants in the chat.
         *
         * @param responseObserver the stream observer used to send chat messages to the client
         * @return a StreamObserver used to receive messages from the client
         */
        @Override
        public StreamObserver<ClientMessage> chatStream(StreamObserver<ChatMessages> responseObserver) {
            return new StreamObserver<ClientMessage>() {
                String sessionID = null;
                String username = null;

                /**
                 * Processes incoming client messages received through the bidirectional stream.
                 * Validates the client's session, adds their session to the active streams if not already present,
                 * and broadcasts the message to other active participants in the chat.
                 *
                 * @param clientMessage the message sent by the client that contains the username, session ID, and message content
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
                 * Handles errors that occur during the bidirectional streaming session.
                 * If a session ID is associated with the current stream, the session is removed
                 * from the list of active chat streams to clean up resources.
                 *
                 * @param throwable the exception or error that triggered this method
                 */
                @Override
                public void onError(Throwable throwable) {
                    if (sessionID != null) {
                        activeChatStreams.remove(sessionID);
                    }
                }

                /**
                 * Completes the bidirectional streaming session for the client.
                 * If the session ID is associated with the current stream, it is removed
                 * from the list of active chat streams to ensure resources are cleaned up.
                 * Once cleanup is complete, signals to the client that the stream has ended successfully.
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
         * Provides a list of all currently registered users.
         *
         * @param request the request message for retrieving the user list
         * @param responseObserver the stream observer used to send the response containing the list of users
         */
        @Override
        public void listUsers(GetUsersMessage request, StreamObserver<UserInfoMessage> responseObserver) {
            if (users.get(request.getUsername()) != request.getSessionID()) {
                responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
            }

            UserInfoMessage userInfoMessage = UserInfoMessage.newBuilder().addAllUsers(users.keySet()).build();
            responseObserver.onNext(userInfoMessage);
            responseObserver.onCompleted();
        }
    }

    /**
     * Entry point for the ChatServer program. This method initializes and starts the gRPC server,
     * registering the ChatService to handle client requests. The server listens on the specified
     * port and awaits termination. A shutdown hook is also added to gracefully stop the server
     * when the program is terminated.
     *
     * @param args the command-line arguments passed to the program
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
            e.printStackTrace();
        }
    }
}
