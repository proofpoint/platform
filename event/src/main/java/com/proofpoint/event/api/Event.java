package com.proofpoint.event.api;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.proofpoint.event.client.EventField;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public abstract class Event
{
    private String name;
    private DateTime timestamp;
    private String host;
    private final UUID uuid;

    private static final String hostname = getHostname();

    public Event()
    {
        this.timestamp = DateTime.now(DateTimeZone.UTC);
        this.host = hostname;
        this.uuid = UUID.randomUUID();
    }

    public String getName()
    {
        return name;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.TIMESTAMP)
    public DateTime getTimestamp()
    {
        return timestamp;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.HOST)
    public String getHost()
    {
        return host;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.UUID)
    public UUID getUuid()
    {
        return uuid;
    }

    protected void setTimestamp(DateTime timestamp)
    {
        Preconditions.checkNotNull(timestamp, "timestamp is null");
        Preconditions.checkArgument(timestamp.getZone().equals(DateTimeZone.UTC));
        this.timestamp = timestamp;
    }

    protected void setHost(String host)
    {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(host));
        this.host = host;
    }

    void setName(String name)
    {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Event that = (Event) o;

        if (!uuid.equals(that.uuid)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    private static String getHostname()
    {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            return "__UNKNOWN_HOST__";
        }
    }
}
