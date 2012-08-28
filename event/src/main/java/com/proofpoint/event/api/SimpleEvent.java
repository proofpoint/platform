package com.proofpoint.event.api;

import com.google.common.base.Strings;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

@EventType
public class SimpleEvent
    extends Event
{
    private final String description;

    SimpleEvent(String description)
    {
        this.description = Strings.nullToEmpty(description);
    }

    @EventField
    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SimpleEvent{");
        sb.append("name='").append(getName()).append('\'');
        sb.append(",timestamp=").append(getTimestamp());
        sb.append(",host='").append(getHost()).append('\'');
        sb.append(",uuid=").append(getUuid());
        sb.append(",description='").append(getDescription()).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
