package com.rivalzin.bettersearch;

import java.lang.reflect.Method;

public final class Log {
    // log4j on the old versions, slf4j on the new ones, System.out on neither
    private static final String[][] BACKENDS = {
            {"org.apache.logging.log4j.LogManager", "org.apache.logging.log4j.Logger"},
            {"org.slf4j.LoggerFactory", "org.slf4j.Logger"},
    };

    private final String name;
    private final Object target;
    private final Method mDebug;
    private final Method mInfo;
    private final Method mWarn;
    private final Method mError;
    private final Method mDebugEnabled;

    private Log(String name, Object target, Method mDebug, Method mInfo, Method mWarn,
                     Method mError, Method mDebugEnabled) {
        this.name = name;
        this.target = target;
        this.mDebug = mDebug;
        this.mInfo = mInfo;
        this.mWarn = mWarn;
        this.mError = mError;
        this.mDebugEnabled = mDebugEnabled;
    }

    public static Log create(String name) {
        for (String[] pair : BACKENDS) {
            try {
                Class<?> factory = Class.forName(pair[0]);
                Class<?> type = Class.forName(pair[1]);
                Object logger = factory.getMethod("getLogger", String.class).invoke(null, name);
                if (logger == null) {
                    continue;
                }
                return new Log(name, logger,
                        type.getMethod("debug", String.class, Object[].class),
                        type.getMethod("info", String.class, Object[].class),
                        type.getMethod("warn", String.class, Object[].class),
                        type.getMethod("error", String.class, Object[].class),
                        type.getMethod("isDebugEnabled"));
            } catch (Throwable ignored) {
            }
        }
        return new Log(name, null, null, null, null, null, null);
    }

    public String backend() {
        return target == null ? "System.out" : target.getClass().getName();
    }

    public boolean debugEnabled() {
        if (mDebugEnabled == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(mDebugEnabled.invoke(target));
        } catch (Throwable e) {
            return false;
        }
    }

    public void debug(String msg, Object... args) {
        if (target != null && !debugEnabled()) {
            return;
        }
        dispatch(mDebug, "DEBUG", msg, args);
    }

    public void info(String msg, Object... args) {
        dispatch(mInfo, "INFO", msg, args);
    }

    public void warn(String msg, Object... args) {
        dispatch(mWarn, "WARN", msg, args);
    }

    public void error(String msg, Object... args) {
        dispatch(mError, "ERROR", msg, args);
    }

    private void dispatch(Method method, String level, String msg, Object[] args) {
        if (target != null && method != null) {
            try {
                method.invoke(target, msg, args == null ? new Object[0] : args);
                return;
            } catch (Throwable e) {
            }
        }
        System.out.println("[" + name + "/" + level + "] " + format(msg, args));
    }

    static String format(String msg, Object[] args) {
        if (msg == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return msg;
        }
        StringBuilder out = new StringBuilder(msg.length() + 32);
        int arg = 0;
        int i = 0;
        while (i < msg.length()) {
            if (i + 1 < msg.length() && msg.charAt(i) == '{' && msg.charAt(i + 1) == '}'
                    && arg < args.length) {
                out.append(String.valueOf(args[arg++]));
                i += 2;
            } else {
                out.append(msg.charAt(i++));
            }
        }

        while (arg < args.length) {
            out.append(' ').append(String.valueOf(args[arg++]));
        }
        return out.toString();
    }
}
