package com.rivalzin.bettersearch;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class FailurePolicy {
    private FailurePolicy() {
    }

    public static void rethrowFatal(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof InvocationTargetException
                || cause instanceof CompletionException
                || cause instanceof ExecutionException)
                && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof Error && !(cause instanceof LinkageError)) {
            throw (Error) cause;
        }
    }
}
