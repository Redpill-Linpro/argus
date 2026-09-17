package com.redpill_linpro.argus.broker;

public class BrokerException extends RuntimeException {

    private final boolean accessDenied;

    public BrokerException(String message) {
        this(message, false, null);
    }

    public BrokerException(String message, Throwable cause) {
        this(message, isSecurityCause(cause), cause);
    }

    public BrokerException(String message, boolean accessDenied, Throwable cause) {
        super(message, cause);
        this.accessDenied = accessDenied;
    }

    public boolean isAccessDenied() {
        return accessDenied;
    }

    public static boolean indicatesAccessDenied(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("does not have permission")
                || message.contains("is not authorized")
                || message.contains("AMQ229123")
                || message.contains("AMQ229212")
                || message.contains("AMQ229213");
    }

    private static boolean isSecurityCause(Throwable cause) {
        while (cause != null) {
            String name = cause.getClass().getName();
            if (name.endsWith("JMSSecurityException") || name.endsWith("ActiveMQSecurityException")) {
                return true;
            }
            if (name.endsWith("ActiveMQNotConnectedException")) {
                return false;
            }
            if (indicatesAccessDenied(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
