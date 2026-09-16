package com.rivalzin.bettersearch.tools;

import com.rivalzin.bettersearch.FailurePolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class FailurePolicyTest {
    public static void main(String[] args) {
        FailurePolicy.rethrowFatal(new IllegalStateException());
        FailurePolicy.rethrowFatal(new NoClassDefFoundError());
        FailurePolicy.rethrowFatal(new InvocationTargetException(new NoSuchMethodError()));
        Error[] errors = {new OutOfMemoryError(), new StackOverflowError(), new ThreadDeath(), new AssertionError()};
        int checks = 3;
        for (Error error : errors) {
            Throwable[] wrappers = {error, new InvocationTargetException(error),
                    new CompletionException(new ExecutionException(new InvocationTargetException(error)))};
            for (Throwable wrapper : wrappers) {
                boolean thrown = false;
                try {
                    FailurePolicy.rethrowFatal(wrapper);
                } catch (Error actual) {
                    if (actual != error) {
                        throw new AssertionError("Fatal error identity was lost");
                    }
                    thrown = true;
                }
                if (!thrown) {
                    throw new AssertionError("Fatal error was swallowed");
                }
                checks++;
            }
        }
        System.out.println("FailurePolicyTest: " + checks + " checks passed");
    }
}
