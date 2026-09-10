package com.redpill_linpro.argus.broker;

import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.Protocol;

public final class BrokerClientFactory {

    private BrokerClientFactory() {
    }

    public static BrokerClient create(ConnectionProfile profile) {
        if (profile.protocol() == Protocol.CORE) {
            return new CoreBrokerClient(profile);
        }
        return new OpenWireBrokerClient(profile);
    }
}
