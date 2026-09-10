package com.redpill_linpro.argus;

import java.io.IOException;
import java.util.Optional;

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
        ConnectionDialog dialog = new ConnectionDialog(profileStore);
        Optional<ConnectionProfile> profile = dialog.showAndWait();
        dialog.shutdownExecutor();
        BrokerClient client = dialog.createdClient();
        if (profile.isEmpty() || client == null) {
            Platform.exit();
            return;
        }
        try {
            openMainWindow(stage, profile.get(), client);
        } catch (IOException e) {
            Platform.exit();
        }
    }

    private void openMainWindow(Stage stage, ConnectionProfile profile, BrokerClient client) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main-view.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.init(profile, client);

        Scene scene = new Scene(root, 1280, 800);
        var css = MainController.class.getResource("styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("Argus - " + profile.name() + " (" + profile.displayUrl() + ")");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            controller.shutdown();
            Platform.exit();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
