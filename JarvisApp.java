import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Random;

public class JarvisApp extends Application {

    private TextField inputField;
    private Button sendButton;
    private Button clearButton;
    private Button uploadButton;
    private Button speakButton;
    private Button micButton;
    private Button stopSpeakButton;
    private ProgressIndicator loader;
    private Label statusLabel;
    private Label micIndicator;

    private String lastJarvisReply = "";
    private DustEffectView dustEffect;

    private Process speechProcess;
    private Process micProcess;
    private Path currentSpeechTextFile;
    private Path selectedUploadedFile;
    private Path sessionHistoryFile;
    private Label attachmentLabel;
    private Button removeAttachmentButton;

    @Override
    public void start(Stage stage) {

        createSessionHistoryFile();

        Label title = new Label("JARVIS Assistant");
        title.setStyle(
                "-fx-text-fill: #00ffcc;" +
                "-fx-font-size: 28px;" +
                "-fx-font-weight: bold;"
        );

        dustEffect = new DustEffectView(950, 440);

        StackPane effectPane = new StackPane(dustEffect);
        effectPane.setPadding(new Insets(10));
        effectPane.setStyle(
                "-fx-background-color: #020814;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #00ffcc;" +
                "-fx-border-radius: 20;" +
                "-fx-border-width: 1;"
        );

        statusLabel = new Label("");
        statusLabel.setStyle(
                "-fx-text-fill: transparent;" +
                "-fx-font-size: 1px;"
        );
        StackPane.setAlignment(statusLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(statusLabel, new Insets(0, 0, 15, 0));
        effectPane.getChildren().add(statusLabel);

        micIndicator = new Label("MIC OFF");
        micIndicator.setStyle(
                "-fx-text-fill: #6f8f99;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-color: rgba(0,0,0,0.35);" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 6 12 6 12;"
        );
        StackPane.setAlignment(micIndicator, Pos.TOP_RIGHT);
        StackPane.setMargin(micIndicator, new Insets(15, 18, 0, 0));
        effectPane.getChildren().add(micIndicator);

        inputField = new TextField();
        inputField.setPromptText("Ask Jarvis anything...");
        inputField.setStyle(
                "-fx-background-color: #101820;" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: #6f8f99;" +
                "-fx-font-size: 15px;" +
                "-fx-background-radius: 15;" +
                "-fx-border-color: #00ffcc;" +
                "-fx-border-radius: 15;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 10;"
        );

        sendButton = new Button("Send");
        clearButton = new Button("Clear");
        uploadButton = new Button("Upload File");
        speakButton = new Button("Speak");
        micButton = new Button("Mic");
        stopSpeakButton = new Button("Stop Speaking");

        styleButton(sendButton);
        styleButton(clearButton);
        styleButton(uploadButton);
        styleButton(speakButton);
        styleButton(micButton);
        styleStopButton(stopSpeakButton);

        loader = new ProgressIndicator();
        loader.setVisible(false);
        loader.setPrefSize(35, 35);

        attachmentLabel = new Label("No file attached");
        attachmentLabel.setStyle(
                "-fx-text-fill: #6f8f99;" +
                "-fx-font-size: 13px;" +
                "-fx-background-color: #08111d;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #153847;" +
                "-fx-border-radius: 12;" +
                "-fx-padding: 8 12 8 12;"
        );

        removeAttachmentButton = new Button("Remove File");
        styleStopButton(removeAttachmentButton);
        removeAttachmentButton.setVisible(false);
        removeAttachmentButton.setOnAction(e -> clearAttachment());

        HBox attachmentBox = new HBox(10, attachmentLabel, removeAttachmentButton);
        attachmentBox.setPadding(new Insets(0, 10, 0, 10));
        attachmentBox.setAlignment(Pos.CENTER_LEFT);

        HBox inputBox = new HBox(10, inputField, sendButton, uploadButton, speakButton, micButton, stopSpeakButton, clearButton, loader);
        inputBox.setPadding(new Insets(10));
        inputBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        VBox root = new VBox(18, title, effectPane, attachmentBox, inputBox);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #050b14;");
        VBox.setVgrow(effectPane, Priority.ALWAYS);

        sendButton.setOnAction(e -> sendMessage(inputField.getText().trim()));
        inputField.setOnAction(e -> sendMessage(inputField.getText().trim()));

        clearButton.setOnAction(e -> {
            inputField.clear();
            lastJarvisReply = "";
            statusLabel.setText("");
            clearAttachment();
        });

        uploadButton.setOnAction(e -> uploadFile(stage));
        speakButton.setOnAction(e -> speakLastReply());
        micButton.setOnAction(e -> startMicMode());
        stopSpeakButton.setOnAction(e -> stopSpeakingAndDeleteWav());

        Scene scene = new Scene(root, 1000, 650);

        stage.setTitle("JARVIS Assistant");
        stage.setScene(scene);
        stage.show();

        dustEffect.start();
    }

    private void applyButtonVisuals(Button button, String normal, String hover, String pressed) {
        button.setStyle(normal);

        button.setOnMouseEntered(e -> button.setStyle(hover));
        button.setOnMouseExited(e -> button.setStyle(normal));
        button.setOnMousePressed(e -> button.setStyle(pressed));
        button.setOnMouseReleased(e -> {
            if (button.isHover()) {
                button.setStyle(hover);
            } else {
                button.setStyle(normal);
            }
        });
    }

    private void styleButton(Button button) {
        String normal =
                "-fx-background-color: #00ffcc;" +
                "-fx-text-fill: black;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: transparent;" +
                "-fx-border-radius: 12;";

        String hover =
                "-fx-background-color: #8ffff0;" +
                "-fx-text-fill: black;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: white;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        String pressed =
                "-fx-background-color: #007f70;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: #ffee88;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        applyButtonVisuals(button, normal, hover, pressed);
    }

    private void styleStopButton(Button button) {
        String normal =
                "-fx-background-color: #ff3344;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: transparent;" +
                "-fx-border-radius: 12;";

        String hover =
                "-fx-background-color: #ff6670;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: white;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        String pressed =
                "-fx-background-color: #9b0010;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: #ffee88;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        applyButtonVisuals(button, normal, hover, pressed);
    }

    private void styleMicButtonActive() {
        String normal =
                "-fx-background-color: #ffb000;" +
                "-fx-text-fill: black;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: #ff3344;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        String hover =
                "-fx-background-color: #ffd36a;" +
                "-fx-text-fill: black;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: white;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        String pressed =
                "-fx-background-color: #ff4b1f;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 9 15 9 15;" +
                "-fx-border-color: #ffee88;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 2;";

        applyButtonVisuals(micButton, normal, hover, pressed);
    }

    private void sendMessage(String userText) {
        boolean hasFile = selectedUploadedFile != null;

        if (userText.isEmpty() && !hasFile) {
            return;
        }

        String requestText;

        if (hasFile) {
            String taskText = userText.isEmpty()
                    ? "Explain this file clearly and tell the important points."
                    : userText;

            requestText = "__JARVIS_FILE_UPLOAD__\n"
                    + "FILE_PATH=" + selectedUploadedFile.toAbsolutePath().toString() + "\n"
                    + "USER_TASK:\n"
                    + taskText;
        } else {
            requestText = userText;
        }

        inputField.clear();
        setLoading(true);
        statusLabel.setText("");

        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                return runPythonBackend(requestText);
            }
        };

        task.setOnSucceeded(e -> {
            String reply = task.getValue();
            lastJarvisReply = reply;

            setLoading(false);
            statusLabel.setText("");
            if (hasFile) {
                clearAttachment();
            }
            speakText(reply);
        });

        task.setOnFailed(e -> {
            setLoading(false);
            statusLabel.setText("ERROR");
            speakText("Something went wrong.");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void uploadFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose any file for JARVIS");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        selectedUploadedFile = file.toPath();
        attachmentLabel.setText("Attached: " + file.getName());
        attachmentLabel.setStyle(
                "-fx-text-fill: #d9fffb;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-color: #0b1b28;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #00ffcc;" +
                "-fx-border-radius: 12;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 8 12 8 12;"
        );
        removeAttachmentButton.setVisible(true);
        inputField.setPromptText("Type what Jarvis should do with this file, then press Send...");
        inputField.requestFocus();
    }

    private void clearAttachment() {
        selectedUploadedFile = null;
        if (attachmentLabel != null) {
            attachmentLabel.setText("No file attached");
            attachmentLabel.setStyle(
                    "-fx-text-fill: #6f8f99;" +
                    "-fx-font-size: 13px;" +
                    "-fx-background-color: #08111d;" +
                    "-fx-background-radius: 12;" +
                    "-fx-border-color: #153847;" +
                    "-fx-border-radius: 12;" +
                    "-fx-padding: 8 12 8 12;"
            );
        }
        if (removeAttachmentButton != null) {
            removeAttachmentButton.setVisible(false);
        }
        if (inputField != null) {
            inputField.setPromptText("Ask Jarvis anything...");
        }
    }


    private void createSessionHistoryFile() {
        try {
            Path projectFolder = Path.of("").toAbsolutePath();
            Path historyFolder = projectFolder.resolve("Chat History");
            Files.createDirectories(historyFolder);
                        String timestamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            );
                     sessionHistoryFile = historyFolder.resolve("chat_" + timestamp + ".txt");
                     Files.writeString(
                    sessionHistoryFile,
                    "JARVIS SESSION STARTED: " + timestamp + System.lineSeparator() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            sessionHistoryFile = null;
        }
    }

    private void setLoading(boolean loading) {
        sendButton.setDisable(loading);
        inputField.setDisable(loading);
        uploadButton.setDisable(loading);
        speakButton.setDisable(loading);
        micButton.setDisable(loading);
        clearButton.setDisable(loading);
        loader.setVisible(loading);
        dustEffect.setThinking(loading);
    }

    private String runPythonBackend(String question) {
        StringBuilder output = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder("python", "backend.py");
            if (sessionHistoryFile != null) {
                pb.environment().put("JARVIS_HISTORY_FILE", sessionHistoryFile.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)
            );

            writer.write(question);
            writer.flush();
            writer.close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

        } catch (Exception e) {
            return "Java could not run Python backend: " + e.getMessage();
        }

        return output.toString().trim();
    }

    private void speakLastReply() {
        if (lastJarvisReply == null || lastJarvisReply.trim().isEmpty()) {
            statusLabel.setText("");
            speakText("No Jarvis reply to speak yet.");
            return;
        }

        statusLabel.setText("");
        speakText(lastJarvisReply);
    }

    private void speakText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }


        Thread speechThread = new Thread(() -> {
            try {
                currentSpeechTextFile = Files.createTempFile("jarvis_voice_", ".txt");

                Files.writeString(
                        currentSpeechTextFile,
                        text,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE
                );

                String safePath = currentSpeechTextFile.toString().replace("\\", "\\\\").replace("'", "''");

                String command =
                        "Add-Type -AssemblyName System.Speech; " +
                        "$text = Get-Content -Raw -Encoding UTF8 '" + safePath + "'; " +
                        "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$speak.Rate = 0; " +
                        "$speak.Volume = 100; " +
                        "$speak.Speak($text); " +
                        "Remove-Item '" + safePath + "';";

                ProcessBuilder pb = new ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-Command",
                        command
                );

                speechProcess = pb.start();

                Platform.runLater(() -> {
                    dustEffect.setSpeaking(true);
                });

                speechProcess.waitFor();

            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText(""));
            } finally {
                try {
                    if (currentSpeechTextFile != null && Files.exists(currentSpeechTextFile)) {
                        Files.deleteIfExists(currentSpeechTextFile);
                    }
                } catch (Exception ignored) {}

                speechProcess = null;
                currentSpeechTextFile = null;

                Platform.runLater(() -> {
                    dustEffect.setSpeaking(false);
                    statusLabel.setText("");
                });
            }
        });

        speechThread.setDaemon(true);
        speechThread.start();
    }


    private void startMicMode() {
        try {
            dustEffect.setMicMode(true);
            styleMicButtonActive();
            micIndicator.setText("MIC ACTIVE");
            micIndicator.setStyle(
                    "-fx-text-fill: #ffdd66;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-color: rgba(80,20,0,0.65);" +
                    "-fx-background-radius: 12;" +
                    "-fx-padding: 6 12 6 12;" +
                    "-fx-border-color: #ff5533;" +
                    "-fx-border-radius: 12;" +
                    "-fx-border-width: 1;"
            );
            inputField.setPromptText("Listening... speak now");

            if (micProcess != null && micProcess.isAlive()) {
                statusLabel.setText("");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder("python", "-u", "voiceass.py");
            if (sessionHistoryFile != null) {
                pb.environment().put("JARVIS_HISTORY_FILE", sessionHistoryFile.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(true);
            micProcess = pb.start();

            startMicOutputReader();

            statusLabel.setText("");
        } catch (Exception e) {
            dustEffect.setMicMode(false);
            styleButton(micButton);
            micIndicator.setText("MIC ERROR");
            statusLabel.setText("");
            speakText("Mic mode could not start.");
        }
    }

    private void startMicOutputReader() {
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(micProcess.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    String micLine = line.trim();

                    if (micLine.isEmpty()) {
                        continue;
                    }

                    Platform.runLater(() -> handleMicOutput(micLine));
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    micIndicator.setText("MIC OUTPUT ERROR");
                    dustEffect.setMicMode(false);
                    styleButton(micButton);
                });
            }
        });

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handleMicOutput(String micLine) {
        String lower = micLine.toLowerCase();

        if (lower.startsWith("heard:")) {
            String heardText = micLine.substring(6).trim();

            if (!heardText.isEmpty()) {
                inputField.setText(heardText);
                inputField.positionCaret(inputField.getText().length());
                dustEffect.pulseMicDetected();
                micIndicator.setText("HEARD: " + shortenForIndicator(heardText));
            }

            return;
        }

        if (lower.startsWith("you:")) {
            String heardText = micLine.substring(4).trim();

            if (!heardText.isEmpty()) {
                inputField.setText(heardText);
                inputField.positionCaret(inputField.getText().length());
                dustEffect.pulseMicDetected();
                micIndicator.setText("YOU SAID: " + shortenForIndicator(heardText));
            }

            return;
        }

        if (lower.contains("listening") || lower.contains("voice activation")) {
            micIndicator.setText("LISTENING...");
            dustEffect.setMicMode(true);
            return;
        }

        if (lower.contains("wake word detected")) {
            micIndicator.setText("WAKE WORD DETECTED");
            dustEffect.pulseMicDetected();
            return;
        }

        if (lower.contains("microphone error") || lower.contains("error")) {
            micIndicator.setText("MIC ERROR");
            dustEffect.setMicMode(false);
            styleButton(micButton);
        }
    }

    private String shortenForIndicator(String text) {
        if (text.length() <= 28) {
            return text;
        }

        return text.substring(0, 28) + "...";
    }

    private void stopSpeakingAndDeleteWav() {
        try {
            // Stop JavaFX PowerShell speaking
            if (speechProcess != null && speechProcess.isAlive()) {
                speechProcess.destroyForcibly();
            }

            // Tell Python voiceass.py to stop if you add stop-file checking there
            Files.writeString(
                    Path.of("stop_speaking.txt"),
                    "stop",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            // Delete Python WAV file if it exists
            Files.deleteIfExists(Path.of("jarvis_reply.wav"));

            // Delete Java temp speech text file if it exists
            if (currentSpeechTextFile != null) {
                Files.deleteIfExists(currentSpeechTextFile);
            }

            if (micProcess != null && micProcess.isAlive()) {
                micProcess.destroyForcibly();
            }
            micProcess = null;

            dustEffect.setSpeaking(false);
            dustEffect.setMicMode(false);
            styleButton(micButton);
            micIndicator.setText("MIC OFF");
            micIndicator.setStyle(
                    "-fx-text-fill: #6f8f99;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-color: rgba(0,0,0,0.35);" +
                    "-fx-background-radius: 12;" +
                    "-fx-padding: 6 12 6 12;"
            );
            inputField.setPromptText("Ask Jarvis anything...");
            statusLabel.setText("");

        } catch (Exception e) {
            statusLabel.setText("");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class DustEffectView extends Canvas {

        private final Random random = new Random();

        private final int particleCount = 2400;
        private final double sphereRadius = 220;

        private final Particle[] particles = new Particle[particleCount];

        private double angleX = 0;
        private double angleY = 0;
        private double angleZ = 0;

        private final double speedX = randomDirection() * randomRange(0.0005, 0.0015);
        private final double speedY = randomDirection() * randomRange(0.0005, 0.0015);
        private final double speedZ = randomDirection() * randomRange(0.0003, 0.0009);

        private double time = 0;

        private volatile boolean speaking = false;
        private volatile boolean thinking = false;
        private volatile boolean micMode = false;
        private double targetThinkingLevel = 0.0;
        private double currentVoiceLevel = 0.0;
        private double targetVoiceLevel = 0.0;
        private double currentThinkingLevel = 0.0;
        private double targetMicLevel = 0.0;
        private double currentMicLevel = 0.0;
        private double externalVoicePulse = 0.0;

        private AnimationTimer timer;

        public DustEffectView(double width, double height) {
            super(width, height);
            createParticles();
        }

        public void start() {
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    draw();
                }
            };
            timer.start();
        }

        public void setSpeaking(boolean value) {
            speaking = value;
            targetVoiceLevel = value ? 1.0 : 0.0;
        }

        public void setThinking(boolean value) {
            thinking = value;
            targetThinkingLevel = value ? 1.0 : 0.0;
        }

        public void setMicMode(boolean value) {
            micMode = value;
            targetMicLevel = value ? 1.0 : 0.0;
        }

        public void pulseMicDetected() {
            externalVoicePulse = 1.0;
        }

        private void createParticles() {
            for (int i = 0; i < particleCount; i++) {
                double theta = randomRange(0, 2 * Math.PI);
                double phi = Math.acos(randomRange(-1, 1));

                double r = sphereRadius * Math.pow(random.nextDouble(), 0.55);

                double x = r * Math.sin(phi) * Math.cos(theta);
                double y = r * Math.sin(phi) * Math.sin(theta);
                double z = r * Math.cos(phi);

                particles[i] = new Particle(
                        x,
                        y,
                        z,
                        randomRange(0, 100),
                        randomRange(0.5, 2.5),
                        randomRange(0.2, 1.2),
                        random.nextInt(4) == 0 ? 2 : 1
                );
            }
        }

        private void draw() {
            GraphicsContext gc = getGraphicsContext2D();

            double width = getWidth();
            double height = getHeight();

            gc.setFill(Color.rgb(2, 6, 14));
            gc.fillRect(0, 0, width, height);

            double centerX = width / 2;
            double centerY = height / 2;

            time += 0.035;

            angleX += speedX;
            angleY += speedY;
            angleZ += speedZ;

            // Smooth transition
            currentVoiceLevel += (targetVoiceLevel - currentVoiceLevel) * 0.045;
            currentThinkingLevel += (targetThinkingLevel - currentThinkingLevel) * 0.035;
            currentMicLevel += (targetMicLevel - currentMicLevel) * 0.045;
            externalVoicePulse *= 0.90;

            // Fake voice volume wave while Jarvis is speaking
            double voicePulse = 0.0;

            if (currentVoiceLevel > 0.01) {
                voicePulse =
                        0.55
                        + 0.25 * Math.sin(time * 2.7)
                        + 0.18 * Math.sin(time * 5.3)
                        + 0.10 * Math.sin(time * 9.1);
            
                voicePulse = Math.max(0.15, Math.min(1.0, voicePulse));
            }

            double voiceAmount = currentVoiceLevel * voicePulse;
            double thinkingAmount = currentThinkingLevel;
            double micAmount = currentMicLevel;
                    
            double disturbance = 2
                    + thinkingAmount * 12
                    + voiceAmount * 34
                    + micAmount * 24;

            ProjectedParticle[] projected = new ProjectedParticle[particleCount];

            for (int i = 0; i < particleCount; i++) {
                Particle p = particles[i];

                double x = p.x;
                double y = p.y;
                double z = p.z;

                double distance = Math.sqrt(x * x + y * y + z * z);
                if (distance == 0) {
                    distance = 1;
                }

                double directionFactor = distance / sphereRadius;

                double floatWave = Math.sin(
                        time * p.drift + p.offset + x * 0.015 + y * 0.015
                );

                double dustWave = Math.cos(
                        time * 0.7 + p.offset + z * 0.02
                );

                double wave = (floatWave + dustWave) * disturbance * 0.35 * directionFactor;

                x += (x / distance) * wave;
                y += (y / distance) * wave;
                z += (z / distance) * wave;

                if (currentVoiceLevel > 0.01) {
                    double shakePower = 0.8 + voiceAmount * 2.4;

                    x += randomRange(-shakePower, shakePower);
                    y += randomRange(-shakePower, shakePower);
                    z += randomRange(-shakePower, shakePower);
                }

                x += Math.sin(time * 0.6 + p.offset) * 1.2;
                y += Math.cos(time * 0.5 + p.offset) * 1.2;

                double[] r1 = rotateX(x, y, z, angleX);
                double[] r2 = rotateY(r1[0], r1[1], r1[2], angleY);
                double[] r3 = rotateZ(r2[0], r2[1], r2[2], angleZ);

                x = r3[0];
                y = r3[1];
                z = r3[2];

                double cameraDistance = 650;
                double scale = cameraDistance / (cameraDistance - z);

                double screenX = centerX + x * scale;
                double screenY = centerY + y * scale;

                projected[i] = new ProjectedParticle(z, screenX, screenY, scale, p);
            }

            Arrays.sort(projected, (a, b) -> Double.compare(a.z, b.z));

            for (ProjectedParticle pp : projected) {
                double depth = (pp.z + sphereRadius) / (2 * sphereRadius);
                depth = Math.max(0, Math.min(depth, 1));

                double twinkle = Math.sin(time * pp.particle.twinkle + pp.particle.offset);
                double twinkleStrength = (twinkle + 1) / 2;

                double dotSize = pp.particle.size * pp.scale;

                dotSize += voiceAmount * 0.7;
                dotSize += micAmount * 0.9;

                dotSize = Math.max(1, Math.min(dotSize, 2.6));

                int r;
                int g;
                int b;

                if (micAmount > 0.05) {
                    int brightness = (int) (90 + depth * 115 + twinkleStrength * 50 + micAmount * 55);
                    r = Math.min(255, brightness + 90);
                    g = Math.min(225, (int) (55 + depth * 85 + twinkleStrength * 80 + micAmount * 80));
                    b = Math.min(95, (int) (8 + twinkleStrength * 28));
                } else if (voiceAmount > 0.05) {
                    int brightness = (int) (90 + depth * 120 + twinkleStrength * 35 + voiceAmount * 45);
                    r = (int) (35 + twinkleStrength * 80 + voiceAmount * 40);
                    g = Math.min(255, brightness + 40);
                    b = Math.min(255, brightness + 80);
                } else if (thinkingAmount > 0.05) {
                    int brightness = (int) (60 + depth * 100 + twinkleStrength * 30 + thinkingAmount * 30);
                    r = 20;
                    g = Math.min(230, brightness + 50);
                    b = Math.min(255, brightness + 90);
                } else {
                    int brightness = (int) (45 + depth * 90 + twinkleStrength * 25);
                    r = (int) (15 + twinkleStrength * 25);
                    g = Math.min(180, brightness + 45);
                    b = Math.min(220, brightness + 80);
                
                }

                gc.setFill(Color.rgb(r, g, b));
                gc.fillOval(pp.x, pp.y, dotSize, dotSize);

                if (dotSize > 2 && twinkleStrength > 0.85) {
                    gc.setStroke(Color.rgb(r / 2, g / 2, b / 2));
                    gc.strokeOval(pp.x - 1, pp.y - 1, 4, 4);
                }
            }
        }

        private double[] rotateX(double x, double y, double z, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double y2 = y * cos - z * sin;
            double z2 = y * sin + z * cos;

            return new double[]{x, y2, z2};
        }

        private double[] rotateY(double x, double y, double z, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double x2 = x * cos + z * sin;
            double z2 = -x * sin + z * cos;

            return new double[]{x2, y, z2};
        }

        private double[] rotateZ(double x, double y, double z, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double x2 = x * cos - y * sin;
            double y2 = x * sin + y * cos;

            return new double[]{x2, y2, z};
        }

        private double randomRange(double min, double max) {
            return min + random.nextDouble() * (max - min);
        }

        private double randomDirection() {
            return random.nextBoolean() ? 1 : -1;
        }

        private static class Particle {
            double x;
            double y;
            double z;
            double offset;
            double twinkle;
            double drift;
            int size;

            Particle(double x, double y, double z, double offset, double twinkle, double drift, int size) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.offset = offset;
                this.twinkle = twinkle;
                this.drift = drift;
                this.size = size;
            }
        }

        private static class ProjectedParticle {
            double z;
            double x;
            double y;
            double scale;
            Particle particle;

            ProjectedParticle(double z, double x, double y, double scale, Particle particle) {
                this.z = z;
                this.x = x;
                this.y = y;
                this.scale = scale;
                this.particle = particle;
            }
        }
    }
}
