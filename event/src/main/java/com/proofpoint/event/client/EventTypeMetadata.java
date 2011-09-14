package com.proofpoint.event.client;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newTreeMap;
import static com.proofpoint.event.client.AnnotationUtils.findAnnotatedMethods;
import static com.proofpoint.event.client.EventDataType.getEventDataType;
import static com.proofpoint.event.client.TypeParameterUtils.getTypeParameters;

class EventTypeMetadata<T>
{
    public static Set<EventTypeMetadata<?>> getValidEventTypeMetaDataSet(Class<?>... eventClasses)
    {
        ImmutableSet.Builder<EventTypeMetadata<?>> set = ImmutableSet.builder();
        for (Class<?> eventClass : eventClasses) {
            set.add(getValidEventTypeMetadata(eventClass));
        }
        return set.build();
    }

    public static <T> EventTypeMetadata<T> getValidEventTypeMetadata(Class<T> eventClass)
    {
        EventTypeMetadata<T> metadata = getEventTypeMetadata(eventClass);
        if (!metadata.getErrors().isEmpty()) {
            String errors = Joiner.on('\n').join(metadata.getErrors());
            throw new IllegalArgumentException(String.format("Invalid event class [%s]:%n%s", eventClass.getName(), errors));
        }
        return metadata;
    }

    public static <T> EventTypeMetadata<T> getEventTypeMetadata(Class<T> eventClass)
    {
        return new EventTypeMetadata<T>(eventClass, Lists.<String>newArrayList(), Maps.<Class<?>, EventTypeMetadata<?>>newHashMap(), false);
    }

    private final Class<T> eventClass;
    private final String typeName;
    private final EventFieldMetadata uuidField;
    private final EventFieldMetadata timestampField;
    private final EventFieldMetadata hostField;
    private final List<EventFieldMetadata> fields;
    private final List<String> errors;

    private EventTypeMetadata(Class<T> eventClass, List<String> errors, Map<Class<?>, EventTypeMetadata<?>> metadataClasses, boolean nestedEvent)
    {
        Preconditions.checkNotNull(eventClass, "eventClass is null");
        Preconditions.checkNotNull(errors, "errors is null");
        Preconditions.checkNotNull(metadataClasses, "metadataClasses is null");
        Preconditions.checkState(!metadataClasses.containsKey(eventClass), "metadataClasses contains eventClass");

        this.eventClass = eventClass;
        this.errors = errors;

        // handle cycles in the object graph
        // these values must not be used until after construction
        metadataClasses.put(eventClass, this);

        // get type name from annotation or class name
        String typeName = eventClass.getSimpleName();
        if (!eventClass.isAnnotationPresent(EventType.class)) {
            addClassError("is not annotated with @%s", EventType.class.getSimpleName());
        }
        else {
            EventType typeAnnotation = eventClass.getAnnotation(EventType.class);
            if (!typeAnnotation.value().isEmpty()) {
                if (nestedEvent) {
                    addClassError("specifies an event name but is used as a nested event");
                }
                typeName = typeAnnotation.value();
            }
        }
        this.typeName = typeName;

        // build event field metadata
        List<EventFieldMetadata> uuidFields = newArrayList();
        List<EventFieldMetadata> timestampFields = newArrayList();
        List<EventFieldMetadata> hostFields = newArrayList();
        Map<String, EventFieldMetadata> fields = newTreeMap();

        for (Method method : findAnnotatedMethods(eventClass, EventField.class)) {
            // validate method
            if (method.getParameterTypes().length != 0) {
                addMethodError("does not have zero parameters", method);
                continue;
            }

            Class<?> dataType = method.getReturnType();
            boolean iterable = false;

            if (isIterable(method.getReturnType())) {
                dataType = getIterableType(method);
                if (dataType == null) {
                    continue;
                }
                iterable = true;
            }

            EventDataType eventDataType = null;
            EventTypeMetadata<?> nestedType = null;

            if (isNestedEvent(dataType)) {
                nestedType = getNestedEventTypeMetadata(dataType, metadataClasses);
            }
            else {
                eventDataType = getEventDataType(dataType);
                if (eventDataType == null) {
                    addMethodError("%s type [%s] is not supported", method, (iterable ? "iterable" : "return"), dataType);
                    continue;
                }
            }

            EventField eventField = method.getAnnotation(EventField.class);
            String fieldName = eventField.value();
            String v1FieldName = null;

            if (eventField.fieldMapping() != EventField.EventFieldMapping.DATA) {
                // validate special fields
                if (nestedEvent) {
                    addMethodError("non-DATA fieldMapping (%s) not allowed for nested event", method, eventField.fieldMapping());
                    continue;
                }
                if (!fieldName.isEmpty()) {
                    addMethodError("has a value and non-DATA fieldMapping (%s)", method, eventField.fieldMapping());
                    continue;
                }
                fieldName = eventField.fieldMapping().getFieldName();
            }
            else {
                if (fieldName.isEmpty()) {
                    fieldName = extractRawNameFromGetter(method);
                }
                // always lowercase the first letter, even for user specified names
                v1FieldName = fieldName;
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                if (fields.containsKey(fieldName)) {
                    addClassError("Multiple methods are annotated for @X field [%s]", fieldName);
                    continue;
                }
            }

            EventFieldMetadata eventFieldMetadata = new EventFieldMetadata(fieldName, v1FieldName, method, eventDataType, nestedType, iterable);
            switch (eventField.fieldMapping()) {
                case HOST:
                    hostFields.add(eventFieldMetadata);
                    break;
                case TIMESTAMP:
                    timestampFields.add(eventFieldMetadata);
                    break;
                case UUID:
                    uuidFields.add(eventFieldMetadata);
                    break;
                case DATA:
                    fields.put(fieldName, eventFieldMetadata);
                    break;
                default:
                    throw new AssertionError("unhandled fieldMapping type: " + eventField.fieldMapping());
            }
        }

        findInvalidMethods(eventClass);

        if (!uuidFields.isEmpty() && uuidFields.size() > 1) {
            addClassError("Multiple methods are annotated for @X(fieldMapping=%s)", EventField.EventFieldMapping.UUID);
        }
        this.uuidField = Iterables.getFirst(uuidFields, null);

        if (!timestampFields.isEmpty() && timestampFields.size() > 1) {
            addClassError("Multiple methods are annotated for @X(fieldMapping=%s)", EventField.EventFieldMapping.TIMESTAMP);
        }
        this.timestampField = Iterables.getFirst(timestampFields, null);

        if (!hostFields.isEmpty() && hostFields.size() > 1) {
            addClassError("Multiple methods are annotated for @X(fieldMapping=%s)", EventField.EventFieldMapping.HOST);
        }
        this.hostField = Iterables.getFirst(hostFields, null);

        this.fields = Ordering.from(EventFieldMetadata.NAME_COMPARATOR).immutableSortedCopy(fields.values());

        if (getErrors().isEmpty() && this.fields.isEmpty()) {
            addClassError("does not have any @X annotations");
        }
    }

    private Class<?> getIterableType(Method method)
    {
        Type[] types = getTypeParameters(Iterable.class, method.getGenericReturnType());
        if ((types == null) || (types.length != 1)) {
            addMethodError("Unable to get type parameter for iterable [%s]", method, method.getGenericReturnType());
            return null;
        }
        Type type = types[0];
        if (!(type instanceof Class)) {
            addMethodError("Iterable type parameter [%s] must be an exact type", method, type);
            return null;
        }
        if (isIterable((Class<?>) type)) {
            addMethodError("Iterable of iterable is not supported", method);
            return null;
        }
        return (Class<?>) type;
    }

    @SuppressWarnings("unchecked")
    private EventTypeMetadata<?> getNestedEventTypeMetadata(Class<?> eventClass, Map<Class<?>, EventTypeMetadata<?>> metadataClasses)
    {
        EventTypeMetadata<?> metadata = metadataClasses.get(eventClass);
        if (metadata != null) {
            return metadata;
        }

        // the constructor adds itself to the list of classes
        return new EventTypeMetadata(eventClass, errors, metadataClasses, true);
    }

    private void findInvalidMethods(Class<T> eventClass)
    {
        // find invalid methods that were skipped by findAnnotatedMethods()
        for (Class<?> clazz = eventClass; clazz != null; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventField.class)) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        addMethodError("is not public", method);
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        addMethodError("is static", method);
                    }
                }
            }
        }
    }

    private static String extractRawNameFromGetter(Method method)
    {
        String name = method.getName();
        if (name.length() > 3 && name.startsWith("get")) {
            return name.substring(3);
        }
        if (name.length() > 2 && name.startsWith("is")) {
            return name.substring(2);
        }
        return name;
    }

    private static boolean isIterable(Class<?> type)
    {
        return Iterable.class.isAssignableFrom(type);
    }

    private static boolean isNestedEvent(Class<?> type)
    {
        return type.isAnnotationPresent(EventType.class);
    }

    List<String> getErrors()
    {
        return ImmutableList.copyOf(errors);
    }

    public Class<T> getEventClass()
    {
        return eventClass;
    }

    public String getTypeName()
    {
        return typeName;
    }

    public EventFieldMetadata getUuidField()
    {
        return uuidField;
    }

    public EventFieldMetadata getTimestampField()
    {
        return timestampField;
    }

    public EventFieldMetadata getHostField()
    {
        return hostField;
    }

    public List<EventFieldMetadata> getFields()
    {
        return fields;
    }

    public void addMethodError(String format, Method method, Object... args)
    {
        String prefix = String.format("@X method [%s] ", method.toGenericString());
        addClassError(prefix + format, args);
    }

    public void addClassError(String format, Object... args)
    {
        String message = String.format(format, args);
        message = String.format("Event class [%s] %s", eventClass, message);
        message = message.replace("@X", EventField.class.getSimpleName());
        errors.add(message);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EventTypeMetadata<?> that = (EventTypeMetadata<?>) o;

        if (eventClass != null ? !eventClass.equals(that.eventClass) : that.eventClass != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return eventClass != null ? eventClass.hashCode() : 0;
    }
}
