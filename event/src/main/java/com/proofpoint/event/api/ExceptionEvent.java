package com.proofpoint.event.api;

import com.google.common.base.Preconditions;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;

@EventType
public class ExceptionEvent
    extends SimpleEvent
{
    private final Throwable exception;

    ExceptionEvent(String description, Throwable exception)
    {
        super(description);
        Preconditions.checkNotNull(exception);

        this.exception = exception;
    }

    @EventField
    public String getException()
    {
        return exception.toString();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("ExceptionEvent{");
        sb.append("name='").append(getName()).append('\'');
        sb.append(",timestamp=").append(getTimestamp());
        sb.append(",host='").append(getHost()).append('\'');
        sb.append(",uuid=").append(getUuid());
        sb.append(",description='").append(getDescription()).append('\'');
        sb.append(",exception='").append(getException()).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
