package com.redpill_linpro.argus.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.redpill_linpro.argus.model.MessageDraft;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

public final class MessageDraftFactory {

    private MessageDraftFactory() {
    }

    public static Message create(Session session, MessageDraft draft) throws JMSException {
        Message message;
        if (draft.kind() == MessageDraft.Kind.BYTES_UTF8) {
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes((draft.body() == null ? "" : draft.body()).getBytes(StandardCharsets.UTF_8));
            message = bytesMessage;
        } else {
            message = session.createTextMessage(draft.body() == null ? "" : draft.body());
        }
        if (draft.properties() != null) {
            for (Map.Entry<String, String> entry : draft.properties().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                message.setObjectProperty(entry.getKey(), coerce(entry.getValue()));
            }
        }
        return message;
    }

    public static Object coerce(String value) {
        String v = value.trim();
        if (v.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false")) return Boolean.FALSE;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignored) {
        }
        return value;
    }
}
