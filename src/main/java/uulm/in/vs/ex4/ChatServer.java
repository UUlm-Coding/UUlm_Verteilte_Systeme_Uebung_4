package uulm.in.vs.ex4;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

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

    /**
     * ChatService is a gRPC service implementation for handling chat functionalities including
     * user login and logout. It extends ChatGrpc.ChatImplBase and overrides its methods to provide
     * the core server-side logic for client requests.
     * <p>
     * The service maintains a mapping of usernames to unique session IDs to track active users
     * and ensure session consistency during the lifetime of the server.
     */
    public static class ChatService extends ChatGrpc.ChatImplBase {
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
