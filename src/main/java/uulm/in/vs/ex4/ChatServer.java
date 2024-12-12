package uulm.in.vs.ex4;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private final static ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    public static class ChatService extends ChatGrpc.ChatImplBase {
        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
            String username = request.getUsername();
            if (users.containsKey(username)) {
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("Username already taken")
                        .asRuntimeException());
                return;
            }
            String sessionID = UUID.randomUUID().toString();
            users.put(username, sessionID);
            LoginResponse response = LoginResponse.newBuilder().setSessionID(sessionID).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
            String username = request.getUsername();
            if (!users.containsKey(username)) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("SessionID not found")
                        .asRuntimeException());
                return;
            }
            users.remove(username);
            responseObserver.onNext(LogoutResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
