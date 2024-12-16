package com.example.mecha_server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailSender {

    public static void sendEmail(String email, String subject, String content) {
        Properties prop = new Properties();
        prop.put("mail.smtp.auth", true);
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        Dotenv dotenv = Dotenv.load();
        System.out.println(dotenv.get("EMAIL_USERNAME") + " " + dotenv.get("EMAIL_PASSWORD"));
        Session session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(dotenv.get("EMAIL_USERNAME"), dotenv.get("EMAIL_PASSWORD"));
            }
        });

        MimeMessage message = new MimeMessage(session);
        try {
            message.setFrom(new InternetAddress(dotenv.get("EMAIL_USERNAME")));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject(subject);
            // message.setText(content);
            message.setContent(content, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("Email sent successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

public class ChatServer {
    private static final String DB_URL = "jdbc:mysql://localhost:4321/chat_application";
    private static final String DB_USERNAME = "user";
    private static final String DB_PASSWORD = "1234";

    private static final int PORT = 12345;
    private static Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>(); // to keep track of all
                                                                                             // connected client

    public static void main(String[] args) {
        // EmailSender.sendEmail(
        // "tnpkhang22@clc.fitus.edu.vn",
        // "Test email",
        // "<!doctype html>\r\n" + //
        // "<html>\r\n" + //
        // " <head>\r\n" + //
        // " <title>This is the title of the webpage!</title>\r\n" + //
        // " </head>\r\n" + //
        // " <body>\r\n" + //
        // " <p>This is an example paragraph. Anything in the <strong>body</strong> tag
        // will appear on the page, just like this <strong>p</strong> tag and its
        // contents.</p>\r\n"
        // + //
        // " </body>\r\n" + //
        // "</html>");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int userId;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(clientSocket.getInputStream());

                while (true) {
                    String action = (String) in.readObject();
                    System.out.println("request: " + action);
                    try {
                        handleAction(action);
                    } catch (IOException | ClassNotFoundException | SQLException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error handling client: " + e.getMessage());
            }
        }

        private void disconnect() {
            connectedClients.remove(userId);
            try {
                if (out != null)
                    out.close();
                if (in != null)
                    in.close();
                if (clientSocket != null)
                    clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
        }

        private void handleAction(String action) throws IOException, ClassNotFoundException, SQLException {
            if ("LOGIN".equals(action)) {
                String username = (String) in.readObject();
                String password = (String) in.readObject();

                // User info is user_id and fullname
                List<String> userInfo = authenticateUser(username, password);
                if (userInfo != null) {
                    int userId = Integer.parseInt(userInfo.get(0));
                    String fullname = userInfo.get(1);
                    connectedClients.put(userId, this);
                    System.out.println("login client " + userInfo.get(1) + " successfully: ");
                    int logId = createNewLog(userId);
                    out.writeObject("SUCCESS");
                    out.writeObject(userId);
                    out.writeObject(username);
                    out.writeObject(fullname);
                    out.writeObject(logId);
                    System.out.println("all current active user: " + connectedClients.size());
                } else {
                    out.writeObject("FAILURE");

                }
            } else if ("SEND_MESSAGE".equals(action)) {
                int senderId = (int) in.readObject();
                int chatId = (int) in.readObject();
                String message = (String) in.readObject();

                Timestamp datetime = insertMessageIntoDatabase(chatId, senderId, message);
                notifyChatParticipants(chatId, senderId, message, datetime);
            } else if ("GET_FRIEND_LIST".equals(action)) {
                int requestId = (int) in.readObject();
                List<String[]> friendList = getFriendForUser(requestId);

                out.writeObject("respond_GET_FRIEND_LIST");
                out.writeObject(friendList);
            } else if ("LOGOUT".equals(action)) {
                int userId = (int) in.readObject();
                int logId = (int) in.readObject();
                connectedClients.remove(userId);
                updateLogSectionEnd(logId);
                System.out.println(
                        "userId: " + userId + " logged out. Current num of active user: " + connectedClients.size());
            } else if ("GET_CHAT_MESSAGES".equals(action)) {
                int chatId = Integer.parseInt((String) in.readObject());

                List<String[]> messages = getChatMessages(chatId);
                out.writeObject("respond_GET_CHAT_MESSAGES");
                out.writeObject(messages);
            } else if ("SIGNUP".equals(action)) {
                String username = (String) in.readObject();
                String fullname = (String) in.readObject();
                String address = (String) in.readObject();
                String email = (String) in.readObject();
                String passwordHash = (String) in.readObject();

                boolean success = registerUser(username, fullname, email, address, passwordHash);
                out.writeObject(success ? "SUCCESS" : "FAILURE");
            } else if ("GET_POTENTIAL_FRIENDS".equals(action)) {
                int userId = (int) in.readObject();

                List<String[]> potentialFriends = getPotentialFriends(userId);
                out.writeObject("respond_GET_POTENTIAL_FRIENDS");
                out.writeObject(potentialFriends);
            } else if ("ADD_FRIEND_REQUEST".equals(action)) {
                int senderId = (int) in.readObject();
                int receiverId = (int) in.readObject();

                insertFriendRequest(senderId, receiverId);
            } else if ("GET_USER_FRIEND_REQUEST".equals(action)) {
                int userId = (int) in.readObject();
                List<String[]> requestSent = getRequestSent(userId);
                out.writeObject("respond_GET_USER_FRIEND_REQUEST");
                out.writeObject(requestSent);
            } else if ("CANCEL_FRIEND_REQUEST".equals(action)) {
                int userId = (int) in.readObject();
                int friendId = (int) in.readObject();
                deleteFriendRequest(userId, friendId);
            } else if ("GET_FRIEND_REQUEST".equals(action)) {
                int userId = (int) in.readObject();
                List<String[]> requestList = getFriendRequest(userId);
                out.writeObject("respond_GET_FRIEND_REQUEST");
                out.writeObject(requestList);
            } else if ("ACCEPT_FRIEND_REQUEST".equals(action)) {
                int receiverId = (int) in.readObject();
                int senderId = (int) in.readObject();
                removeFriendRequest(senderId, receiverId);
                addNewFriendships(senderId, receiverId);
                List<Integer> userIdList = Arrays.asList(senderId, receiverId);
                addNewChat("", true, senderId, userIdList);
            } else if ("DECLINE_FRIEND_REQUEST".equals(action)) {
                int receiverId = (int) in.readObject();
                int senderId = (int) in.readObject();
                removeFriendRequest(senderId, receiverId);
            } else if ("BLOCK_USER".equals(action)) {
                int blockerID = (int) in.readObject();
                int chatId = (int) in.readObject();
                blockUser(blockerID, chatId);
            } else if ("REMOVE_BLOCK".equals(action)) {
                int blockerId = (int) in.readObject();
                int blockedId = (int) in.readObject();

                removeBlockList(blockerId, blockedId);
                System.out.println("remove Block complete");
            } else if ("GET_BLOCKED_LIST".equals(action)) {
                int userId = (int) in.readObject();
                List<String[]> blockedList = getBlockedList(userId);
                out.writeObject("respond_GET_BLOCKED_LIST");
                out.writeObject(blockedList);
            } else if ("SEND_EMAIL".equals(action)) {
                String email = (String) in.readObject();
                String subject = (String) in.readObject();
                String content = (String) in.readObject();
                sendEmail(email, subject, content);
            } else if ("GET_USER_PROFILE".equals(action)) {
                int userId = (int) in.readObject();

                List<String> userInfo = getUserInfo(userId);
                out.writeObject("respond_GET_USER_PROFILE");
                out.writeObject(userInfo);
            } else if ("UPDATE_USER_PROFILE".equals(action)) {
                // info order: user_id, fullname, gender, address, email, date_of_birth
                int userId = (int) in.readObject();
                String fullname = (String) in.readObject();
                String gender = (String) in.readObject();
                String address = (String) in.readObject();
                String email = (String) in.readObject();
                String dob = (String) in.readObject();

                LocalDate localDate = LocalDate.parse(dob);
                Date sqlDate = Date.valueOf(localDate);

                updateUserInfo(userId, fullname, gender, address, email, sqlDate);
            } else if ("CHECK_PASSWORD".equals(action)) {
                int userId = (int) in.readObject();
                String password = (String) in.readObject();
                out.writeObject("respond_CHECK_PASSWORD");
                if (password.equals(getPassword(userId))) {
                    out.writeObject("PASSWORD_CORRECT");
                } else {
                    out.writeObject("PASSWORD_WRONG");  
                }    
            } else if ("UPDATE_PASSWORD".equals(action)){
                int userId = (int) in.readObject();
                String oldPassword = (String) in.readObject();
                String newPassword = (String) in.readObject();
                out.writeObject("respond_UPDATE_PASSWORD");
                if (oldPassword.equals(getPassword(userId))) {
                    updatePassword(userId, newPassword);
                    out.writeObject("SUCCESS");
                } else {
                    out.writeObject("FAILURE");
                }
            } else if ("FORGOT_PASSWORD".equals(action)) {
                String email = (String) in.readObject();
                out.writeObject("respond_FORGOT_PASSWORD");
                try {
                    String reponse = updatePasswordRandomly(email) ? "SUCCESS" : "FAILURE";
                    out.writeObject(reponse);
                    System.out.println("forgot password complete"); 
                } catch (NoSuchAlgorithmException | SQLException e) {
                    e.printStackTrace();
                }
            } else if ("REPORT_USER".equals(action)) {
                int userId = (int) in.readObject();
                int chatId = (int) in.readObject();
                String reason = (String) in.readObject();
                reportUserInChat(userId, chatId, reason);
            } else if ("GET_REPORT_LIST".equals(action)){
                int userId = (int) in.readObject();

                List<String[]> reportedList = getReportedList(userId);
                out.writeObject("respond_GET_REPORT_LIST");
                // order: reportedUserId, reportedUserFullname, reportedUserStatus, reportedTime
                out.writeObject(reportedList);
            } else {
                System.out.println("Unknown action: " + action);
            }
        }

        private int getUserIdByEmail(String email) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM users WHERE email = ?");
            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            userId = rs.getInt("user_id");
            
            return userId;
        }
        
        private boolean checkIfEmailExist(String email) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM users WHERE email = ?");
            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
        
        private boolean updatePasswordRandomly(String email) throws SQLException, NoSuchAlgorithmException {
            if (!checkIfEmailExist(email)) {
                System.out.println("User not found");
                return false;
            }
            
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt);

            String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}\\|;:\'\",<.>/?";
            String newPassword = RandomStringUtils.random(15, characters);
            System.out.println(newPassword);

            byte[] hashedPassword = md.digest(newPassword.getBytes(StandardCharsets.UTF_8));

            int userId = getUserIdByEmail(email);
            if (userId == 0) {
                System.out.println("User not found");
                return false;
            }
            updatePassword(userId, hashedPassword.toString());
            sendEmail(email, "New Password", "Your new password is: " + newPassword);
            System.out.println("update password complete");
            
            return true;
        }

        private void reportUserInChat(int reporterId, int chatId, String reason) throws SQLException{
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT user_id
                FROM chat_members
                WHERE chat_id = ? AND user_id != ? 
            """);

            stmt.setInt(1, chatId);
            stmt.setInt(2, reporterId);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            int reportedUserId = rs.getInt("user_id");

            stmt = conn.prepareStatement("INSERT INTO Report (reporter_id, reported_id, reason) VALUES (?, ?, ?)");
            stmt.setInt(1, reporterId);
            stmt.setInt(2, reportedUserId);
            stmt.setString(3, reason);

            stmt.executeUpdate();

            System.out.println("User reported!");
        }

        private void updatePassword(int userId, String newPassword) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        UPDATE users
                        SET password_hash = ?
                        WHERE user_id = ?
                    """);
            stmt.setString(1, newPassword);
            stmt.setInt(2, userId);

            stmt.executeUpdate();
        }

        private String getPassword(int userId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT password_hash FROM users WHERE user_id = ?
                    """);
            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getString("password_hash");
        }

        private void updateUserInfo(int userId, String fullname, String gender, String address, String email, Date dob)
                throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        UPDATE users
                        SET full_name = ?, gender = ?, address = ?, email = ?, date_of_birth = ?
                        WHERE user_id = ?
                    """);

            stmt.setString(1, fullname);
            stmt.setString(2, gender);
            stmt.setString(3, address);
            stmt.setString(4, email);
            stmt.setDate(5, dob);
            stmt.setInt(6, userId);

            stmt.executeUpdate();
            System.out.println("update info complete");
        }

        private void updateLogSectionEnd(int logID) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        UPDATE log_history
                        SET section_end = NOW()
                        WHERE log_id = ?
                    """);
            stmt.setInt(1, logID);

            stmt.executeUpdate();
        }

        private int createNewLog(int userId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        INSERT INTO log_history (user_id, section_start) VALUES (?, NOW())
                    """);
            stmt.setInt(1, userId);
            stmt.executeUpdate();

            stmt = conn.prepareStatement("SELECT LAST_INSERT_ID() as newLogId");
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int logId = rs.getInt("newLogId");

            return logId;
        }

        private List<String> getUserInfo(int userId) throws SQLException {
            List<String> userInfo = new ArrayList<>();
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT * FROM users WHERE user_id = ?
                    """);
            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String fullname = rs.getString("full_name");
                String email = rs.getString("email");
                String gender = rs.getString("gender");
                Date dob = rs.getDate("date_of_birth");
                String address = rs.getString("address");

                String dobFormatted = dob != null ? new SimpleDateFormat("yyyy-MM-dd").format(dob) : "N/A";

                Collections.addAll(userInfo, fullname, email, gender, dobFormatted, address);
            } else {
                System.out.println("error fetch user data");
            }

            return userInfo;
        }

        private void sendEmail(String email, String subject, String content) {

        }

        private List<String[]> getReportedList(int userId) throws SQLException {
            List <String[]> reportedList = new ArrayList<>();
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT r.reported_id, u.full_name, r.status, r.created_at
                FROM Report r JOIN users u ON r.reported_id = u.user_id
                WHERE r.reporter_id = ?
            """);
            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()){
                String reportedUserId = rs.getString("r.reported_id");
                String reportedUserFullname = rs.getString("u.full_name");
                String reportedUserStatus = rs.getString("r.status");
                String reportedTime = rs.getString("r.created_at");

                reportedList.add(new String[] {reportedUserId, reportedUserFullname, reportedUserStatus, reportedTime});
            }
            
            return reportedList;
        }

        private List<String[]> getBlockedList(int userId) throws SQLException {
            List<String[]> blockedList = new ArrayList<>();
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT b.blocked_id, u.full_name
                        FROM Blocked_List b JOIN users u ON b.blocked_id = u.user_id
                        WHERE b.blocker_id = ?
                    """);

            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String blockedId = rs.getString("b.blocked_id");
                String fullname = rs.getString("u.full_name");
                System.out.println("Block user: " + blockedId + ", " + fullname);
                blockedList.add(new String[] { blockedId, fullname });
            }
            return blockedList;
        }

        private boolean removeBlockList(int blockerId, int blockedId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        DELETE FROM Blocked_List WHERE blocker_id = ? AND blocked_id = ?
                    """);

            stmt.setInt(1, blockerId);
            stmt.setInt(2, blockedId);

            return stmt.executeUpdate() == 1 ? true : false;
        }

        private void blockUser(int blockerId, int chatId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            // Remove friendships first
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT user_id FROM chat_members WHERE (chat_id = ? AND user_id != ?)
                    """);

            stmt.setInt(1, chatId);
            stmt.setInt(2, blockerId);

            ResultSet rs = stmt.executeQuery();
            int blockedId = 0;
            if (rs.next()) {
                blockedId = rs.getInt("user_id");
            } else {
                System.out.println("Error blocking user");
            }

            // Add info into Blocked_List table
            stmt = conn.prepareStatement("""
                        INSERT INTO Blocked_List (blocker_id, blocked_id) VALUES (?, ?)
                    """);
            stmt.setInt(1, blockerId);
            stmt.setInt(2, blockedId);
            stmt.executeUpdate();
            removeFriendShip(blockerId, blockedId);
            removeChat(chatId);
        }

        private void removeChat(int chatId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            // remove chat members first
            PreparedStatement stmt = conn.prepareStatement("""
                        DELETE FROM chat_members WHERE chat_id = ?
                    """);

            stmt.setInt(1, chatId);

            stmt.executeUpdate();
            System.out.println("remove chat mem complete");
            // then remove the chat itself
            stmt = conn.prepareStatement("""
                        DELETE FROM chats WHERE chat_id = ?
                    """);

            stmt.setInt(1, chatId);

            stmt.executeUpdate();
            System.out.println("remove the chat complete");
        }

        private void removeFriendShip(int user1_id, int user2_id) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        DELETE FROM friendships
                        WHERE (user1_id = ? AND user2_id = ?)
                        OR (user1_id = ? AND user2_id = ?)
                    """);

            stmt.setInt(1, user1_id);
            stmt.setInt(2, user2_id);
            stmt.setInt(3, user2_id);
            stmt.setInt(4, user1_id);

            int row = stmt.executeUpdate();
            System.out.println(row + " friendship deleted");
        }

        private void removeFriendRequest(int senderId, int receiverId) throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        DELETE FROM friend_request WHERE user_id = ? and friend_id = ?
                    """);
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);

            stmt.executeUpdate();
        }

        private void addNewFriendships(int senderId, int receiverId) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                                INSERT INTO friendships (user1_id, user2_id) VALUES (?, ?)
                            """)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.executeUpdate();
                System.out.println("add friendship complete");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void addNewChat(String groupName, boolean isPrivate, int adminId, List<Integer> userIdList)
                throws SQLException {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("""
                        INSERT INTO chats (group_name, chat_type, admin_id) VALUES (?, ?, ?)
                    """);
            if (!groupName.isEmpty())
                stmt.setString(1, groupName);
            else
                stmt.setInt(1, Types.NULL);

            if (isPrivate)
                stmt.setString(2, "private");
            else
                stmt.setString(2, "group");

            stmt.setInt(3, adminId);

            stmt.executeUpdate();

            stmt = conn.prepareStatement("SELECT LAST_INSERT_ID() as newChatId");
            ResultSet rs = null;
            try {
                rs = stmt.executeQuery();
            } catch (SQLException e) {
                System.out.println("cant get last insert id");
                e.printStackTrace();
            }
            int chatId;
            if (rs.next())
                chatId = rs.getInt("newChatId");
            else {
                System.out.println("Error cant found chat id");
                return;
            }

            System.out.println("new inserted chat id: " + chatId);
            for (int userId : userIdList) {
                stmt = conn.prepareStatement("""
                            INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)
                        """);
                stmt.setInt(1, chatId);
                stmt.setInt(2, userId);

                stmt.executeUpdate();
            }
        }

        // Get all of the users that request to be friend with this user(userId)
        private List<String[]> getFriendRequest(int userId) {
            List<String[]> messages = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                                SELECT fr.user_id, u.full_name
                                FROM friend_request fr
                                JOIN users u ON (u.user_id = fr.user_id)
                                WHERE fr.friend_id = ?
                            """)) {
                stmt.setInt(1, userId);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String friendId = rs.getString("fr.user_id");
                    String friendUsername = rs.getString("u.full_name");
                    messages.add(new String[] { friendId, friendUsername });
                }
                System.out.println("remove friend request complete");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return messages;
        }

        private void deleteFriendRequest(int senderId, int receiverId) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                                DELETE FROM friend_request
                                WHERE user_id = ? AND friend_id = ?
                            """)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);

                stmt.executeUpdate();
                System.out.println("remove friend request complete");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private List<String[]> getRequestSent(int userId) {
            List<String[]> messages = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                            SELECT fr.friend_id, u.full_name
                            FROM friend_request fr
                            JOIN users u ON (fr.friend_id = u.user_id)
                            WHERE fr.user_id = ?
                            """)) {
                stmt.setInt(1, userId);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String friendId = rs.getString("fr.friend_id");
                    String friendUsername = rs.getString("u.full_name");
                    messages.add(new String[] { friendId, friendUsername });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return messages;
        }

        private void insertFriendRequest(int senderId, int receiverId) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                            INSERT INTO friend_request (user_id, friend_id) VALUES (?, ?);
                            """)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);

                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private static List<String[]> getPotentialFriends(int userId) {
            List<String[]> messages = new ArrayList<>();
            String query = """
                    SELECT user_id, full_name
                    FROM users
                    WHERE user_id NOT IN (
                        SELECT user1_id FROM friendships WHERE user2_id = ?
                        UNION
                        SELECT user2_id FROM friendships WHERE user1_id = ?
                        UNION
                        SELECT friend_id FROM friend_request WHERE user_id = ?
                        UNION
                        SELECT user_id FROM friend_request WHERE friend_id = ?
                        UNION
                        SELECT blocked_id FROM Blocked_List WHERE blocker_id = ?
                    )
                    AND user_id != ?;
                    """;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                stmt.setInt(3, userId);
                stmt.setInt(4, userId);
                stmt.setInt(5, userId);
                stmt.setInt(6, userId);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String potentialUserId = rs.getString("user_id");
                    String potentialUsername = rs.getString("full_name");
                    messages.add(new String[] { potentialUserId, potentialUsername });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return messages;
        }

        private static Timestamp insertMessageIntoDatabase(int chatId, int senderId, String message) throws SQLException {
            String query = "INSERT INTO messages (chat_id, sender_id, content, created_at) VALUES (?, ?, ?, ?)";
            Timestamp timestamp = Timestamp.from(Instant.now());
            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, chatId);
            stmt.setInt(2, senderId);
            stmt.setString(3, message);
            stmt.setTimestamp(4, timestamp);
            stmt.executeUpdate();
    
            return timestamp;
        }

        private void notifyChatParticipants(int chatId, int senderId, String message, Timestamp timeSent) {
            String query = "SELECT user_id FROM chat_members WHERE chat_id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, chatId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int participantId = rs.getInt("user_id");
                    if (participantId != senderId && connectedClients.containsKey(participantId)) {
                        // System.out.println("userid: " + participantId + ": " +
                        // connectedClients.get(participantId).clientSocket);
                        ClientHandler recipient = connectedClients.get(participantId);
                        try {
                            recipient.out.writeObject("NEW_MESSAGE");
                            recipient.out.writeObject(chatId);
                            recipient.out.writeObject(message);
                            recipient.out.writeObject(senderId);
                            recipient.out.writeObject(timeSent);
                            // System.out.println("send signal to chatid " + chatId + "from userid " +
                            // senderId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private static List<String[]> getChatMessages(int chatId) {
            List<String[]> messages = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                                SELECT sender_id, content, created_at
                                FROM messages
                                WHERE chat_id = ?
                                ORDER BY created_at;
                            """)) {
                stmt.setInt(1, chatId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String sender_id = rs.getString("sender_id");
                    String content = rs.getString("content");
                    String created_at = rs.getString("created_at");
                    messages.add(new String[] { sender_id, content, created_at });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            return messages;
        }

        private static List<String> authenticateUser(String username, String password) {
            List<String> userInfo = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn
                            .prepareStatement("SELECT * FROM users WHERE BINARY username = ? AND password_hash = ?")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String userId = rs.getString("user_id");
                    String fullname = rs.getString("full_name");
                    userInfo.add(userId);
                    userInfo.add(fullname);
                    return userInfo;
                } else
                    return null;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        private static boolean registerUser(String username, String fullname, String email, String address,
                String passwordHash) {
            System.out.println("Registering user: " + username);
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO users (username, email, address, password_hash, full_name, status) " +
                                    "VALUES (?, ?, ?, ?, ?, 'offline')")) {

                stmt.setString(1, username);
                stmt.setString(2, email);
                stmt.setString(3, address);
                stmt.setString(4, passwordHash);
                stmt.setString(5, fullname);

                int rowsInserted = stmt.executeUpdate();
                return rowsInserted > 0;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        private static List<String[]> getFriendForUser(int userId) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                    PreparedStatement stmt = conn.prepareStatement("""
                                SELECT c.chat_id, u.full_name, u.status
                                FROM users u
                                JOIN friendships f ON (u.user_id = f.user1_id OR u.user_id = f.user2_id)
                                JOIN chat_members c ON (c.user_id IN(f.user1_id, f.user2_id))
                                WHERE (f.user1_id = ? OR f.user2_id = ?) AND u.user_id != ?
                                GROUP BY c.chat_id, u.user_id
                                HAVING COUNT(c.chat_id) = 2;
                            """)) {

                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                stmt.setInt(3, userId);
                List<String[]> friendList = new ArrayList<>();
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    friendList.add(new String[] { rs.getString("c.chat_id"), rs.getString("u.full_name"),
                            rs.getString("u.status") });
                }
                return friendList;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
