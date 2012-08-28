package com.proofpoint.event.api;

import com.google.common.base.Preconditions;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;
import com.proofpoint.units.Duration;

@EventType
public class OperationEvent
    extends SimpleEvent
{
    private final boolean success;
    private final int status;
    private final Duration duration;

    OperationEvent(String description, boolean success, int status, Duration duration)
    {
        super(description);
        Preconditions.checkNotNull(duration);

        this.success = success;
        this.status = status;
        this.duration = duration;
    }

    @EventField
    public boolean getSuccess()
    {
        return success;
    }

    @EventField
    public int getStatus()
    {
        return status;
    }

    @EventField
    public long getDuration()
    {
        return Math.round(duration.toMillis());
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("OperationEvent{");
        sb.append("name='").append(getName()).append('\'');
        sb.append(",timestamp=").append(getTimestamp());
        sb.append(",host='").append(getHost()).append('\'');
        sb.append(",uuid=").append(getUuid());
        sb.append(",description='").append(getDescription()).append('\'');
        sb.append(",success=").append(getSuccess());
        sb.append(",status=").append(getStatus());
        sb.append(",duration=").append(getDuration());
        sb.append('}');
        return sb.toString();
    }
}
