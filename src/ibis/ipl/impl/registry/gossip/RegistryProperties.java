package ibis.ipl.impl.registry.gossip;

import ibis.util.TypedProperties;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegistryProperties {

    public static final String PREFIX = "ibis.registry.gossip.";

    public static final String GOSSIP_INTERVAL = PREFIX + "gossip.interval";

    public static final String BOOTSTRAP_LIST = PREFIX + "bootstrap.list";

    public static final String WITNESSES_REQUIRED =
        PREFIX + "witnesses.required";

    public static final String PEER_DEAD_TIMEOUT = PREFIX + "peer.dead.timeout";

    public static final String PING_INTERVAL = PREFIX + "ping.interval";

    public static final String ELECTION_TIMEOUT = PREFIX + "eleciton.timeout";

    public static final String STATISTICS = PREFIX + "statistics";

    public static final String STATISTICS_INTERVAL =
        PREFIX + " statistics.interval";

    // list of descriptions and defaults
    private static final String[][] propertiesList =
        new String[][] {
                { GOSSIP_INTERVAL, "1", "How often do we gossip (in seconds)" },
                { BOOTSTRAP_LIST, null, "List of peers to bootstrap of off" },
                { WITNESSES_REQUIRED, "5",
                        "Int: how many peers need to agree before a node is declared dead" },
                { PEER_DEAD_TIMEOUT, "120",
                        "Number of seconds until a peer can be declared dead" },
                { PING_INTERVAL, "1",
                        "How often do we try to reach a suspect member (in seconds)" },
                { ELECTION_TIMEOUT, "5",
                        "Number of seconds until we return the value of an election" },
                {STATISTICS, "false",
                        "Boolean: if true, statistics are kept and written to a file named statistics/POOL_NAME/ID" },
                { STATISTICS_INTERVAL, "60",
                        "Int: how often statistics are written to disk (in seconds)" },

        };

    public static TypedProperties getHardcodedProperties() {
        TypedProperties properties = new TypedProperties();

        for (String[] element : propertiesList) {
            if (element[1] != null) {
                properties.setProperty(element[0], element[1]);
            }
        }

        return properties;
    }

    public static Map<String, String> getDescriptions() {
        Map<String, String> result = new LinkedHashMap<String, String>();

        for (String[] element : propertiesList) {
            result.put(element[0], element[2]);
        }

        return result;
    }

}