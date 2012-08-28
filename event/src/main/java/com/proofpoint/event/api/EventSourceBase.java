package com.proofpoint.event.api;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.event.client.EventDispatcher;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public abstract class EventSourceBase
{
    private boolean initialized = false;
    private boolean enabled = false;
    private String name;
    private List<String> categories;
    private String description;
    private final Class<?> originClass;

    @Inject
    private static EventDispatcher dispatcher;

    public EventSourceBase()
    {
        originClass = getEventOriginClass(this.getClass());
    }

    protected boolean isActive()
    {
        if (dispatcher == null) {
            return false;
        }
        if (!initialized) {
            return true;
        }
        if (!enabled) {
            return false;
        }

        //TODO: implement
        return true;
    }

    protected void dispatch(Event event)
    {
        if (dispatcher == null) {
            return;
        }
        if (!initialized) {
            initialize();
        }
        if (!enabled) {
            return;
        }

        event.setName(name);
        dispatcher.dispatch(event, this);
    }

    protected static String safeFormat(String format, Object ... args)
    {
        String s;

        if (format == null) {
            s = "";
        }
        else {
            try {
                s = String.format(format, args);
            }
            catch (Throwable ex) {
                s = String.format("%s (%s)", ex.getMessage(), format);
            }
        }

        return s;
    }

    private synchronized void initialize()
    {
        if (initialized) {
            return;
        }

        try {
            Field thisField = null;
            for (Field field : originClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        if (Objects.equal(this, field.get(null))) {
                            thisField = field;
                            break;
                        }
                    }
                    catch (IllegalAccessException ignore) {
                    }
                }
            }
            if (thisField == null) {
                throw new RuntimeException(String.format("%s: Could not find static event source field %s", originClass.getName(), this));
            }

            EventSource source = thisField.getAnnotation(EventSource.class);
            if (source == null) {
                throw new RuntimeException(String.format("%s: Missing @EventSource annotation for event source field %s", originClass.getName(), this));
            }
            name = source.name();
            if (Strings.isNullOrEmpty(name)) {
                throw new RuntimeException(String.format("%s: Name not defined in @EventSource annotation for event source field %s", originClass.getName(), this));
            }
            categories = ImmutableList.copyOf(source.categories());
            description = source.description();

            dispatcher.registerSource(this);
            enabled = true;
        }
        finally {
            initialized = true;
        }
    }

    private static Class<?> getEventOriginClass(Class<?> thisClass)
    {
        String thisClassName = thisClass.getName();

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean foundThisClass = false;
        for (StackTraceElement element : stack) {
            if (!foundThisClass) {
                if (element.getClassName().equals(thisClassName)) {
                    foundThisClass = true;
                }
            }
            else {
                if (!element.getClassName().equals(thisClassName)) {
                    try {
                        return Class.forName(element.getClassName());
                    }
                    catch (ClassNotFoundException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to retrieve event origin class");
    }
}
