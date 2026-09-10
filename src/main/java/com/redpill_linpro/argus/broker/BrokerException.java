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

    private static boolean isSecurityCause(Throwable cause) {
        while (cause != null) {
            String name = cause.getClass().getName();
            if (name.endsWith("JMSSecurityException")
                    || name.endsWith("ActiveMQSecurityException")
                    || name.endsWith("ActiveMQNotConnectedException")) {
                return name.endsWith("JMSSecurityException")
                        || name.endsWith("ActiveMQSecurityException");
            }
            cause = cause.getCause();
        }
        return false;
    }
}
