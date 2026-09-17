package com.redpill_linpro.argus.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public final class AddAddressDialog extends Dialog<AddAddressDialog.Result> {

    public record Result(String address, String queue) {
    }

    private final TextField addressField = new TextField();
    private final TextField queueField = new TextField();

    public AddAddressDialog() {
        setTitle("Argus - add address");
        setHeaderText("Add a destination to the tree manually");
        ButtonType add = new ButtonType("Add", ButtonType.OK.getButtonData());
        getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16, 16, 8, 16));
        grid.add(new Label("Address"), 0, 0);
        grid.add(addressField, 1, 0);
        addressField.setPromptText("address name");
        grid.add(new Label("Queue (optional)"), 0, 1);
        grid.add(queueField, 1, 1);
        queueField.setPromptText("queue to browse under the address");
        getDialogPane().setContent(grid);

        Button addButton = (Button) getDialogPane().lookupButton(add);
        addButton.disableProperty().bind(addressField.textProperty().isEmpty());
        setResultConverter(button -> add.equals(button)
                ? new Result(addressField.getText(), queueField.getText())
                : null);
    }
}
