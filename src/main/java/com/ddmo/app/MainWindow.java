package com.ddmo.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainWindow extends Application {

    private Stage primaryStage;
    private double xOffset = 0;
    private double yOffset = 0;
    private JavaBridge javaBridge;
    private final AtomicBoolean userAction = new AtomicBoolean(false);
    private AnimationTimer keepMaximizedTimer = null;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.javaBridge = new JavaBridge();

        DdmoApplication.startInBackground(getParameters().getRaw().toArray(new String[0]));

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Show");
        stage.setMinWidth(900);
        stage.setMinHeight(700);

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        WebEngine webEngine = webView.getEngine();

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("ddmo", javaBridge);
                webEngine.executeScript(
                    "if(window.onDdmoBridgeReady) window.onDdmoBridgeReady();"
                );

                webEngine.executeScript(
                    "window.resizeTo = function() { console.warn('resizeTo blocked'); }; " +
                    "window.moveTo = function() { console.warn('moveTo blocked'); };"
                );

                try {
                    java.lang.reflect.Field field = webEngine.getClass().getDeclaredField("page");
                    field.setAccessible(true);
                    Object page = field.get(webEngine);
                    java.lang.reflect.Method setBackgroundColor = page.getClass().getDeclaredMethod("setBackgroundColor", int.class);
                    setBackgroundColor.setAccessible(true);
                    setBackgroundColor.invoke(page, 0);
                    try {
                        java.lang.reflect.Method setPageFill = webView.getClass().getMethod("setPageFill", javafx.scene.paint.Paint.class);
                        setPageFill.invoke(webView, Color.TRANSPARENT);
                    } catch (Exception e2) { }
                } catch (Exception e) { }
            }
        });

        String splashHtml = "<!DOCTYPE html><html><head>" +
            "<style>" +
            "html, body { background: transparent !important; margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; font-family: 'Microsoft YaHei', sans-serif; }" +
            ".splash-container { background-color:#0f172a; border-radius: 12px; width: 100%; height: 100%; display:flex; flex-direction:column; justify-content:center; align-items:center; position:absolute; top:0; left:0; z-index:9999; }" +
            ".ddmo-spinner{position:relative;width:120px;height:120px;display:flex;justify-content:center;align-items:center;}" +
            ".ddmo-spinner::before,.ddmo-spinner::after{content:'';position:absolute;border-radius:50%;}" +
            ".ddmo-spinner::before{width:100%;height:100%;box-shadow:inset 0 0 20px #8b5cf6,0 0 20px #3b82f6;animation:ddmo-pulse 2s linear infinite;}" +
            ".ddmo-spinner::after{width:80%;height:80%;border:3px solid transparent;border-top-color:#3b82f6;border-bottom-color:#8b5cf6;animation:ddmo-spin 1.5s cubic-bezier(0.68,-0.55,0.265,1.55) infinite;}" +
            ".ddmo-logo{font-weight:700;font-size:32px;color:#ffffff;text-shadow:0 0 10px #8b5cf6,0 0 20px #8b5cf6;letter-spacing:4px;}" +
            ".ddmo-text{margin-top:40px;color:#94a3b8;font-size:16px;letter-spacing:4px;animation:ddmo-fade 1.5s infinite alternate;}" +
            "@keyframes ddmo-pulse{0%{transform:scale(0.9);opacity:0.7}50%{transform:scale(1.1);opacity:1}100%{transform:scale(0.9);opacity:0.7}}" +
            "@keyframes ddmo-spin{100%{transform:rotate(360deg)}}" +
            "@keyframes ddmo-glow{from{text-shadow:0 0 10px #8b5cf6,0 0 20px #8b5cf6}to{text-shadow:0 0 20px #3b82f6,0 0 30px #3b82f6}}" +
            "@keyframes ddmo-fade{from{opacity:0.3}to{opacity:1}}" +
            "</style></head><body>" +
            "<div class='splash-container'><div class='ddmo-spinner'><span class='ddmo-logo'>Show</span></div><div class='ddmo-text'>咻，咻咻...</div></div></body></html>";

        webEngine.loadContent(splashHtml);

        DdmoApplication.getPortFuture().thenAccept(port -> {
            Platform.runLater(() -> {
                webEngine.load("http://localhost:" + port);
            });
        });

        StackPane root = new StackPane(webView);
        double windowRadius = 12.0;
        Background roundedBackground = new Background(
            new BackgroundFill(Color.WHITE, new CornerRadii(windowRadius), null)
        );
        root.setBackground(roundedBackground);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(windowRadius * 2);
        clip.setArcHeight(windowRadius * 2);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        Scene scene = new Scene(root, 1300, 750);
        scene.setFill(Color.TRANSPARENT);

        // 最大化监听器 + 持续监控定时器
        stage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
            if (isNowMax) {
                stage.setResizable(false);
                if (keepMaximizedTimer == null) {
                    keepMaximizedTimer = new AnimationTimer() {
                        @Override
                        public void handle(long now) {
                            // 如果窗口不是最大化且没有用户主动操作，则立即恢复最大化
                            if (!stage.isMaximized() && !userAction.get()) {
                                stage.setMaximized(true);
                            }
                        }
                    };
                    keepMaximizedTimer.start();
                }
            } else if (wasMax && !isNowMax) {
                if (!userAction.getAndSet(false)) {
                    // 非用户操作（如下拉框触发的退出最大化），立即恢复
                    stage.setMaximized(true);
                } else {
                    // 用户主动退出最大化，停止监控
                    if (keepMaximizedTimer != null) {
                        keepMaximizedTimer.stop();
                        keepMaximizedTimer = null;
                    }
                    stage.setResizable(true);
                }
            } else {
                stage.setResizable(true);
                if (keepMaximizedTimer != null) {
                    keepMaximizedTimer.stop();
                    keepMaximizedTimer = null;
                }
            }
        });

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() {
        DdmoApplication.shutdown();
        Platform.exit();
        System.exit(0);
    }

    public class JavaBridge {

        public void minimize() {
            Platform.runLater(() -> primaryStage.setIconified(true));
        }

        public void maximize() {
            userAction.set(true);
            Platform.runLater(() -> primaryStage.setMaximized(!primaryStage.isMaximized()));
        }

        public void close() {
            Platform.runLater(() -> {
                primaryStage.close();
                DdmoApplication.shutdown();
                Platform.exit();
                System.exit(0);
            });
        }

        public void startDrag(double screenX, double screenY) {
            Platform.runLater(() -> {
                if (primaryStage.isMaximized()) {
                    userAction.set(true);
                    primaryStage.setMaximized(false);
                    primaryStage.setX(screenX - primaryStage.getWidth() / 2);
                    primaryStage.setY(screenY - 20);
                }
                xOffset = screenX - primaryStage.getX();
                yOffset = screenY - primaryStage.getY();
            });
        }

        public void drag(double screenX, double screenY) {
            Platform.runLater(() -> {
                primaryStage.setX(screenX - xOffset);
                primaryStage.setY(screenY - yOffset);
            });
        }

        public boolean isMaximized() {
            return primaryStage.isMaximized();
        }

        public boolean saveFile(String suggestedName, String base64Data) {
            if (base64Data == null || base64Data.isBlank()) {
                return false;
            }
            try {
                String fileName = (suggestedName == null || suggestedName.isBlank()) ? "export.csv" : suggestedName.trim();
                FileChooser chooser = new FileChooser();
                chooser.setTitle("保存导出文件");
                chooser.setInitialFileName(fileName);
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件 (*.csv)", "*.csv"));

                java.io.File file = chooser.showSaveDialog(primaryStage);
                if (file == null) {
                    return false;
                }

                String cleanBase64 = base64Data;
                int comma = cleanBase64.indexOf(',');
                if (comma >= 0) {
                    cleanBase64 = cleanBase64.substring(comma + 1);
                }

                byte[] bytes = Base64.getDecoder().decode(cleanBase64);
                Files.write(file.toPath(), bytes);
                return true;
            } catch (IllegalArgumentException | IOException ex) {
                return false;
            }
        }
    }
}
