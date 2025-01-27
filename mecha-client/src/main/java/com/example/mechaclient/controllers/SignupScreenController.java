package com.example.mechaclient.controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.example.mechaclient.ChatApplication;
import com.example.mechaclient.models.UserSession;
import com.example.mechaclient.models.UserSession.ServerMessageListener;
import com.example.mechaclient.utils.NotificationUtil;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SignupScreenController implements ServerMessageListener{
    
    @FXML private Button loginButton;
    @FXML private TextField usernameField;
    @FXML private TextField fullnameField;
    @FXML private TextField addressField;
    @FXML private TextField emailField;
    @FXML private ChoiceBox genderChoiceBox;
    @FXML private DatePicker dobDatePicker;
    @FXML private TextField passwordField;
    @FXML private TextField confirmPasswordField;

    
    @FXML
    private Button signInButton;
    
    @FXML
    public void initialize() {
        // Add event handlers
        UserSession.getInstance().addMessageListener(this);
        loginButton.setOnAction(event -> {
            handleLogin();
            System.out.println("login pressed");
        });
        signInButton.setOnAction(event -> handleEmailSignIn(event));

        genderChoiceBox.setItems(FXCollections.observableArrayList("Male", "Female", "Other"));
        genderChoiceBox.setValue("Male");
    }
    
    private void handleLogin() {
        try {
            UserSession.socket.close();
            FXMLLoader fxmlLoader = new FXMLLoader(ChatApplication.class.getResource("/views/LoginScreen.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);
    
            // Get the current stage (window)
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                UserSession.getInstance().Logout();
            });
            stage.setScene(scene);   
            UserSession.getInstance().removeMessageListener(this);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void handleEmailSignIn(Event event) {
        String username = usernameField.getText();
        String fullname = fullnameField.getText();
        String address = addressField.getText();
        String email = emailField.getText();
        String gender = (String) genderChoiceBox.getValue();
        LocalDate dob = dobDatePicker.getValue();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validate inputs
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            NotificationUtil.showNotification("Missing field", "All fields are required.");
            System.out.println("All fields are required.");
            return;
        }

        if (!isValidEmail(email)) {
            NotificationUtil.showNotification("Email error", "Invalid email format.");
            System.out.println("Invalid email format.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            NotificationUtil.showNotification("Passwords error", "Passwords do not match.");
            System.out.println("Passwords do not match.");
            return;
        }
        
        String passwordHash = hashPassword(password);
        try {
            UserSession.out.writeObject("SIGNUP");
            UserSession.out.writeObject(username);
            UserSession.out.writeObject(fullname);
            UserSession.out.writeObject(gender);
            UserSession.out.writeObject(dob.format(DateTimeFormatter.ISO_LOCAL_DATE));
            UserSession.out.writeObject(address);
            UserSession.out.writeObject(email);
            UserSession.out.writeObject(passwordHash); 
        } catch (Exception e) {
            e.printStackTrace();
        }

        
    }

    private String hashPassword(String password) {
        return password; // TODO: Implement a secure hashing function
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }

    @Override
    public void onMessageReceived(String serverMessage) {
        try {
            if ("SUCCESS".equals(serverMessage)) {
                
                // System.out.println("User registered successfully.");
                Platform.runLater(() -> {
                    NotificationUtil.showNotification("registration complete", "User registered successfully!");
                    System.out.println("redirect to login screen");
                    handleLogin();
                });
            } else {
                System.out.println("Registration failed.");
            } 
        } catch (Exception e){
            System.out.println("error handling response from friend management controller");
            e.printStackTrace();
        } 
    }
}