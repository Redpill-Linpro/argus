package com.redpill_linpro.argus.launcher;

import com.redpill_linpro.argus.ArgusApp;

import javafx.application.Application;

public final class Launcher {

    public static void main(String[] args) {
        Application.launch(ArgusApp.class, args);
    }

    private Launcher() {
    }
}
