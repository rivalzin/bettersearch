package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.FailurePolicy;
import java.lang.reflect.InvocationTargetException;

public final class IntegrationRetry {
    private long retryAt;
    private int failures;
    private boolean disabled;
    private String reported;

    public synchronized boolean ready(long now) {
        return !disabled && (failures == 0 || now - retryAt >= 0);
    }

    public synchronized void succeeded() {
        retryAt = 0;
        failures = 0;
    }

    public synchronized boolean failed(Throwable failure, long now) {
        Throwable cause = cause(failure);
        FailurePolicy.rethrowFatal(cause);
        disabled = cause instanceof ReflectiveOperationException
                || cause instanceof LinkageError || cause instanceof ClassCastException;
        retryAt = now + (5_000_000_000L << Math.min(failures, 4));
        failures = Math.min(failures + 1, 5);
        String signature = cause.getClass().getName() + ':' + cause.getMessage();
        if (signature.equals(reported)) {
            return false;
        }
        reported = signature;
        return true;
    }

    public synchronized boolean disabled() {
        return disabled;
    }

    public static Throwable cause(Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null
                && failure.getCause() != failure) {
            failure = failure.getCause();
        }
        return failure;
    }
}
