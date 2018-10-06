package com.proofpoint.configuration;

import com.google.inject.Key;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

class ConfigDefaultsHolder<T>
        implements Comparable<ConfigDefaultsHolder<T>>
{
    private static final AtomicLong NEXT_PRIORITY = new AtomicLong();

    private final Key<T> configKey;
    private final Recorder<T> recorder;
    private final long priority = NEXT_PRIORITY.getAndIncrement();
    private final AtomicReference<Replayer<T>> replayer = new AtomicReference<>();

    ConfigDefaultsHolder(Key<T> configKey, Recorder<T> recorder)
    {
        this.configKey = requireNonNull(configKey, "configKey is null");
        this.recorder = requireNonNull(recorder, "recorder is null");
    }

    public Key<T> getConfigKey()
    {
        return configKey;
    }

    public Replayer<T> getReplayer()
    {
        return replayer.updateAndGet(r -> r == null ? recorder.getReplayer() : r);
    }

    @Override
    public int compareTo(ConfigDefaultsHolder<T> o)
    {
        return Long.compare(priority, o.priority);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(recorder, priority);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConfigDefaultsHolder<?> other = (ConfigDefaultsHolder<?>) obj;
        return Objects.equals(this.recorder, other.recorder)
                && Objects.equals(this.priority, other.priority);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("configKey", configKey)
                .add("recorder", recorder)
                .add("priority", priority)
                .toString();
    }
}
