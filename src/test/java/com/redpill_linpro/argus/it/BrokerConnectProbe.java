package com.redpill_linpro.argus.it;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;

public final class BrokerConnectProbe {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        String port = args.length > 1 ? args[1] : "61616";
        String user = args.length > 2 ? args[2] : "";
        String pass = args.length > 3 ? args[3] : "";
        String url = "tcp://" + host + ":" + port;
        System.out.println("PROBE connect " + url + " user='" + user + "'");
        ServerLocator locator = null;
        ClientSessionFactory factory = null;
        ClientSession session = null;
        try {
            locator = ActiveMQClient.createServerLocator(url);
            factory = locator.createSessionFactory();
            session = factory.createSession(
                    user.isBlank() ? null : user,
                    pass.isBlank() ? null : pass,
                    false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE);
            session.start();
            System.out.println("PROBE AUTH OK");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            System.out.println("PROBE CONNECT FAILED: " + e.getClass().getName() + ": " + e.getMessage());
            System.out.println("PROBE ROOT: " + root.getClass().getName() + ": " + root.getMessage());
            return;
        }
        try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
            ClientMessage message = session.createMessage(false);
            ManagementHelper.putOperationInvocation(message, "broker", "getAddressNames");
            ClientMessage reply = requestor.request(message, 10_000);
            if (ManagementHelper.hasOperationSucceeded(reply)) {
                Object result = ManagementHelper.getResult(reply);
                Object[] names = result instanceof Object[] a ? a : new Object[] {result};
                System.out.println("PROBE LISTING OK: " + names.length + " addresses: "
                        + java.util.Arrays.stream(names).limit(10).toList());
            } else {
                Object failure = ManagementHelper.getResult(reply);
                System.out.println("PROBE LISTING DENIED: " + failure);
            }
        } catch (Exception e) {
            System.out.println("PROBE LISTING ERROR: " + e);
        }
        session.close();
        factory.close();
        locator.close();
    }
}
