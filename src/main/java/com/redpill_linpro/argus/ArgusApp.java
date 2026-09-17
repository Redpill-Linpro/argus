package com.redpill_linpro.argus;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.redpill_linpro.argus.broker.BrokerClient;
import com.redpill_linpro.argus.config.ProfileStore;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.ui.ConnectionDialog;
import com.redpill_linpro.argus.ui.MainController;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ArgusApp extends Application {

    private final ProfileStore profileStore = new ProfileStore();

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        while (true) {
            AtomicReference<BrokerClient> clientOut = new AtomicReference<>();
            ConnectionProfile profile = connect(clientOut);
            BrokerClient client = clientOut.get();
            if (profile == null || client == null) {
                Platform.exit();
                return;
            }
            AtomicBoolean disconnected = new AtomicBoolean(false);
            Stage mainWindow = new Stage();
            try {
                openMainWindow(mainWindow, profile, client, () -> {
                    disconnected.set(true);
                    mainWindow.hide();
                });
            } catch (IOException e) {
                Platform.exit();
                return;
            }
            if (!disconnected.get()) {
                Platform.exit();
                return;
            }
        }
    }

    private ConnectionProfile connect(AtomicReference<BrokerClient> clientOut) {
        ConnectionDialog dialog = new ConnectionDialog(profileStore);
        dialog.showAndWait();
        dialog.shutdownExecutor();
        clientOut.set(dialog.createdClient());
        return dialog.connectedProfile();
    }

    private void openMainWindow(Stage stage, ConnectionProfile profile, BrokerClient client,
            Runnable onDisconnected) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main-view.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.init(profile, client);
        controller.setOnDisconnected(onDisconnected);

        Scene scene = new Scene(root, 1280, 800);
        var css = MainController.class.getResource("styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("Argus - " + profile.name() + " (" + profile.displayUrl() + ")");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
