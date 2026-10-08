package com.hermes.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCancellationRegistryTest {

    @Test
    void cancelMarksActiveHandleAndReportsRunning() {
        SessionCancellationRegistry registry = new SessionCancellationRegistry();
        SessionCancellationRegistry.Handle handle = registry.register("session-x");

        assertThat(handle.isCancelled()).isFalse();
        assertThat(registry.cancel("session-x")).isTrue();
        assertThat(handle.isCancelled()).isTrue();
        assertThat(registry.runningHandle("session-x")).isPresent();
    }

    @Test
    void unregisteredHandleCannotBeCancelled() {
        SessionCancellationRegistry registry = new SessionCancellationRegistry();
        SessionCancellationRegistry.Handle handle = registry.register("session-y");
        registry.unregister("session-y", handle);

        assertThat(registry.cancel("session-y")).isFalse();
        assertThat(handle.isCancelled()).isFalse();
        assertThat(registry.runningHandle("session-y")).isEmpty();
    }

    @Test
    void newerRoundReplacesOlderOneAndBothGetCancelled() {
        SessionCancellationRegistry registry = new SessionCancellationRegistry();
        SessionCancellationRegistry.Handle first = registry.register("session-z");
        SessionCancellationRegistry.Handle second = registry.register("session-z");

        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isFalse();

        assertThat(registry.cancel("session-z")).isTrue();
        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isTrue();

        registry.unregister("session-z", first);
        // 第一轮注销不得误删后进轮句柄
        assertThat(registry.runningHandle("session-z")).isPresent();
    }

    @Test
    void nullSessionIsANoOp() {
        SessionCancellationRegistry registry = new SessionCancellationRegistry();
        SessionCancellationRegistry.Handle handle = registry.register(null);

        assertThat(handle.isCancelled()).isFalse();
        assertThat(registry.cancel(null)).isFalse();
        assertThat(registry.runningHandle(null)).isEmpty();
        registry.unregister(null, handle);
    }
}
