package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.MessageProto.Message;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

public class Server2 implements ServerInterface {
    private final int SERVER_ID = 2;
    private final int ADMIN_PORT = 5002;
    private final int CLIENT_PORT = 6002;
    
    private Configuration config;
    private final ConcurrentHashMap<String, Subscriber> subscribers;
    private final ReentrantLock subscriberLock;
    private volatile boolean isRunning;
    private final ConcurrentHashMap<Integer, Socket> peerConnections;
    
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public Server2() {
        this.subscribers = new ConcurrentHashMap<>();
        this.subscriberLock = new ReentrantLock();
        this.peerConnections = new ConcurrentHashMap<>();
    }
    
    public void start() {
        this.isRunning = true;
        
        System.out.println("Server2 başlatılıyor...");
        System.out.println("Admin port: " + ADMIN_PORT);
        System.out.println("Client port: " + CLIENT_PORT);
        
        // Admin bağlantısı için thread
        CompletableFuture.runAsync(() -> {
            try (ServerSocket adminSocket = new ServerSocket(ADMIN_PORT)) {
                System.out.println("Server2 admin bağlantıları için dinleniyor: " + ADMIN_PORT);
                while (isRunning) {
                    Socket socket = adminSocket.accept();
                    new Thread(new AdminHandler(socket, this)).start();
                }
            } catch (IOException e) {
                System.err.println("Admin socket hatası: " + e.getMessage());
            }
        });
        
        // Client bağlantıları için thread
        CompletableFuture.runAsync(() -> {
            try (ServerSocket clientSocket = new ServerSocket(CLIENT_PORT)) {
                System.out.println("Server2 client bağlantıları için dinleniyor: " + CLIENT_PORT);
                while (isRunning) {
                    Socket socket = clientSocket.accept();
                    new Thread(new ClientHandler(socket, this)).start();
                }
            } catch (IOException e) {
                System.err.println("Client socket hatası: " + e.getMessage());
            }
        });
    }
    
    public void stop() {
        this.isRunning = false;
        shutdownLatch.countDown();
    }
    
    public void waitForShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public int getServerId() {
        return SERVER_ID;
    }
    
    @Override
    public Capacity getCapacity() {
        return Capacity.newBuilder()
            .setServerId(SERVER_ID)
            .setServerStatus(subscribers.size())
            .setTimestamp(System.currentTimeMillis())
            .build();
    }
    
    @Override
    public boolean addSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            String subId = String.valueOf(subscriber.getId());
            if (!subscribers.containsKey(subId)) {
                subscribers.put(subId, subscriber);
                syncWithPeers(subscriber);
                return true;
            }
            return false;
        } finally {
            subscriberLock.unlock();
        }
    }
    
    @Override
    public void setConfiguration(Configuration config) {
        this.config = config;
        if (config.getFaultTolerance() > 0) {
            CompletableFuture.runAsync(this::connectToPeers);
        }
    }
    
    private void connectToPeers() {
        if (config.getFaultTolerance() >= 1) {
            connectToPeer(1, "localhost", 5001);
        }
        if (config.getFaultTolerance() >= 2) {
            connectToPeer(3, "localhost", 5003);
        }
    }
    
    private void connectToPeer(int peerId, String host, int port) {
        try {
            Socket peerSocket = new Socket(host, port);
            peerConnections.put(peerId, peerSocket);
            CompletableFuture.runAsync(() -> listenToPeer(peerId, peerSocket));
            System.out.println("Server2: Peer" + peerId + "'e bağlantı başarılı");
        } catch (IOException e) {
            System.err.println("Server2: Peer" + peerId + "'e bağlanılamadı: " + e.getMessage());
        }
    }
    
    private void listenToPeer(int peerId, Socket peerSocket) {
        try {
            InputStream input = peerSocket.getInputStream();
            while (isRunning && !peerSocket.isClosed()) {
                Message message = Message.parseDelimitedFrom(input);
                if (message != null && message.getType() == Message.Type.SYNC) {
                    Subscriber subscriber = Subscriber.parseFrom(message.getPayload());
                    updateSubscriber(subscriber);
                    System.out.println("Server2: Peer" + peerId + "'den senkronizasyon alındı");
                }
            }
        } catch (IOException e) {
            System.err.println("Server2: Peer" + peerId + " bağlantısı koptu: " + e.getMessage());
            peerConnections.remove(peerId);
        }
    }
    
    private void updateSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            subscribers.put(String.valueOf(subscriber.getId()), subscriber);
        } finally {
            subscriberLock.unlock();
        }
    }
    
    private void syncWithPeers(Subscriber subscriber) {
        if (config != null && config.getFaultTolerance() > 0) {
            Message syncMessage = Message.newBuilder()
                .setType(Message.Type.SYNC)
                .setSenderId(String.valueOf(SERVER_ID))
                .setPayload(subscriber.toByteString())
                .setTimestamp(System.currentTimeMillis())
                .build();

            for (Map.Entry<Integer, Socket> peer : peerConnections.entrySet()) {
                sendToPeer(peer.getKey(), syncMessage);
            }
        }
    }
    
    private void sendToPeer(int peerId, Message message) {
        Socket peerSocket = peerConnections.get(peerId);
        if (peerSocket != null && !peerSocket.isClosed()) {
            try {
                message.writeDelimitedTo(peerSocket.getOutputStream());
                System.out.println("Server2: Peer" + peerId + "'e senkronizasyon gönderildi");
            } catch (IOException e) {
                System.err.println("Server2: Peer" + peerId + "'e mesaj gönderilemedi: " + e.getMessage());
                peerConnections.remove(peerId);
            }
        }
    }
    
    public static void main(String[] args) {
        Server2 server = new Server2();
        
        // Shutdown hook ekle
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Server2 kapatılıyor...");
            server.stop();
        }));
        
        server.start();
        System.out.println("Server2 başlatıldı.");
        
        // Ana thread'i beklet
        server.waitForShutdown();
    }
} 