package com.redpill_linpro.argus.util;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.redpill_linpro.argus.model.MessageSnapshot;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;

public final class JmsMessageReader {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private JmsMessageReader() {
    }

    public static MessageSnapshot read(Message message) throws JMSException {
        return new MessageSnapshot(
                message.getJMSMessageID(),
                message.getJMSCorrelationID(),
                message.getJMSTimestamp(),
                message.getJMSExpiration(),
                message.getJMSPriority(),
                deliveryCount(message),
                message.getJMSRedelivered(),
                typeOf(message),
                propertiesOf(message),
                bodyOf(message));
    }

    private static int deliveryCount(Message message) {
        try {
            if (message.propertyExists("JMSXDeliveryCount")) {
                Object value = message.getObjectProperty("JMSXDeliveryCount");
                if (value instanceof Number number) {
                    return number.intValue();
                }
            }
        } catch (JMSException ignored) {
        }
        return 0;
    }

    private static String typeOf(Message message) {
        if (message instanceof TextMessage) return "text";
        if (message instanceof BytesMessage) return "bytes";
        if (message instanceof MapMessage) return "map";
        if (message instanceof StreamMessage) return "stream";
        if (message instanceof ObjectMessage) return "object";
        return message.getClass().getSimpleName();
    }

    private static Map<String, Object> propertiesOf(Message message) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            for (Enumeration<?> names = message.getPropertyNames(); names.hasMoreElements(); ) {
                String name = (String) names.nextElement();
                try {
                    properties.put(name, message.getObjectProperty(name));
                } catch (JMSException ignored) {
                    properties.put(name, "<unreadable>");
                }
            }
        } catch (JMSException ignored) {
        }
        return properties;
    }

    private static String bodyOf(Message message) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            return textMessage.getText();
        }
        if (message instanceof MapMessage mapMessage) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Enumeration<?> names = mapMessage.getMapNames(); names.hasMoreElements(); ) {
                String name = (String) names.nextElement();
                map.put(name, mapMessage.getObject(name));
            }
            return map.toString();
        }
        if (message instanceof BytesMessage bytesMessage) {
            long length = bytesMessage.getBodyLength();
            int read = (int) Math.min(length, MAX_BODY_BYTES);
            byte[] data = new byte[read];
            bytesMessage.readBytes(data);
            if (isPrintable(data)) {
                return "bytes(" + length + "): " + new String(data, StandardCharsets.UTF_8);
            }
            return "bytes(" + length + "):\n" + hexPreview(data, length > read);
        }
        if (message instanceof StreamMessage streamMessage) {
            StringBuilder sb = new StringBuilder("stream: [");
            int count = 0;
            try {
                while (count < 1000) {
                    Object value = streamMessage.readObject();
                    if (count++ > 0) {
                        sb.append(", ");
                    }
                    sb.append(value);
                }
            } catch (JMSException ignored) {
            }
            return sb.append("]").toString();
        }
        if (message instanceof ObjectMessage) {
            return "<serialized object - deserialization disabled>";
        }
        return "<unsupported body: " + message.getClass().getSimpleName() + ">";
    }

    private static boolean isPrintable(byte[] data) {
        for (byte b : data) {
            int v = b & 0xFF;
            boolean control = v < 0x20 && v != '\t' && v != '\n' && v != '\r';
            if (control || v == 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String hexPreview(byte[] data, boolean truncated) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02x", data[i]));
            if ((i + 1) % 32 == 0) {
                sb.append('\n');
            } else if ((i + 1) % 4 == 0) {
                sb.append(' ');
            }
        }
        if (truncated) {
            sb.append("\n...");
        }
        return sb.toString();
    }
}
