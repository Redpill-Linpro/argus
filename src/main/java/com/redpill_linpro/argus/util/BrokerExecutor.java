package com.redpill_linpro.argus.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javafx.application.Platform;

public final class BrokerExecutor {

    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "argus-broker-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public <T> CompletableFuture<T> call(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public void callOnFx(Callable<?> task, Consumer<Throwable> onError) {
        call(task).whenComplete((result, error) -> {
            if (error != null) {
                Platform.runLater(() -> onError.accept(error));
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
