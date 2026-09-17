package com.redpill_linpro.argus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;

import com.redpill_linpro.argus.broker.BrokerException;

class BrokerExceptionTest {

    @Test
    void detectsSecurityExceptionTypesInCauseChain() {
        BrokerException exception = new BrokerException("listing failed",
                new RuntimeException("wrapper", new jakarta.jms.JMSSecurityException("denied")));
        assertTrue(exception.isAccessDenied());
    }

    @Test
    void detectsPermissionMarkersInWrappedMessages() {
        BrokerException core = new BrokerException("management request failed",
                new RuntimeException(
                        "AMQ229123: User: amq does not have permission='CREATE_NON_DURABLE_QUEUE' for queue "
                                + "activemq.management.<uuid> on address activemq.management"));
        assertTrue(core.isAccessDenied());

        BrokerException openWire = new BrokerException("listing failed",
                new JMSException("AMQ229123: User: amq does not have permission='CREATE_NON_DURABLE_QUEUE' "
                        + "for queue ActiveMQ.Advisory.TempQueue on address ActiveMQ.Advisory.TempQueue"));
        assertTrue(openWire.isAccessDenied());
    }

    @Test
    void detectsOtherSecurityCodes() {
        assertTrue(BrokerException.indicatesAccessDenied("AMQ229213: not authorized"));
        assertTrue(BrokerException.indicatesAccessDenied("AMQ229212: alters not allowed"));
        assertTrue(BrokerException.indicatesAccessDenied("user is not authorized to do this"));
        assertFalse(BrokerException.indicatesAccessDenied("connection refused"));
        assertFalse(BrokerException.indicatesAccessDenied(null));
    }

    @Test
    void plainFailuresAreNotAccessDenied() {
        BrokerException exception = new BrokerException("Cannot connect to tcp://host:61616",
                new RuntimeException("connection refused"));
        assertFalse(exception.isAccessDenied());
    }

    @Test
    void notConnectedExceptionStopsSecurityDetection() {
        ActiveMQNotConnectedException notConnected = new ActiveMQNotConnectedException("broker went away");
        BrokerException exception = new BrokerException("send failed", notConnected);
        assertFalse(exception.isAccessDenied());
    }

    private static final class ActiveMQNotConnectedException extends jakarta.jms.JMSException {
        private ActiveMQNotConnectedException(String message) {
            super(message);
        }
    }
}
