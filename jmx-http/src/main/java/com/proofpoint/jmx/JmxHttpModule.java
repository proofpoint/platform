/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.jmx;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.http.server.HttpServerInfo;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.TabularData;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.inject.Scopes.SINGLETON;
import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.json.JsonBinder.jsonBinder;

public class JmxHttpModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        jaxrsBinder(binder).bindAdmin(MBeanResource.class);
        jsonBinder(binder).addSerializerBinding(InetAddress.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(ObjectName.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(OpenType.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(CompositeData.class).to(CompositeDataSerializer.class);
        jsonBinder(binder).addSerializerBinding(TabularData.class).to(TabularDataSerializer.class);

        jaxrsBinder(binder).bindAdmin(ConfigurationResource.class);
        jaxrsBinder(binder).bindAdmin(VersionResource.class);
        jaxrsBinder(binder).bindAdmin(StopAnnouncingResource.class);
        bindConfig(binder).bind(AdminServerConfig.class);
        binder.bind(AdminServerCredentialVerifier.class).in(SINGLETON);

        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement("jmx-http");
        discoveryBinder(binder).bindServiceAnnouncement(new JmxHttpAnnouncementProvider(serviceAnnouncementBuilder));
    }

    static class TabularDataSerializer
            extends StdSerializer<TabularData>
    {
        TabularDataSerializer()
        {
            super(TabularData.class, true);
        }

        @Override
        public void serialize(TabularData data, JsonGenerator jsonGenerator, SerializerProvider provider)
                throws IOException
        {
            jsonGenerator.writeStartArray();

            JsonSerializer<Object> mapSerializer = provider.findValueSerializer(Map.class, null);
            for (Map<String, Object> map : toList(data)) {
                if (!map.isEmpty()) {
                    mapSerializer.serialize(map, jsonGenerator, provider);
                }
            }

            jsonGenerator.writeEndArray();
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        {
            // List<Map<String, Object>
            ObjectNode o = createSchemaNode("array", true);
            o.set("items", createSchemaNode("object", true));
            return o;
        }

    }

    static class CompositeDataSerializer
            extends StdSerializer<CompositeData>
    {
        CompositeDataSerializer()
        {
            super(CompositeData.class, true);
        }

        @Override
        public void serialize(CompositeData data, JsonGenerator jsonGenerator, SerializerProvider provider)
                throws IOException
        {
            Map<String, Object> map = toMap(data);
            if (!map.isEmpty()) {
                jsonGenerator.writeStartObject();
                JsonSerializer<Object> cachedSerializer = null;
                Class<?> cachedType = null;

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey();
                    jsonGenerator.writeFieldName(key);

                    Object value = entry.getValue();

                    // get the serializer, but cache to reduce lookups
                    Class<?> valueType = value.getClass();
                    JsonSerializer<Object> serializer;
                    if (valueType == cachedType) {
                        serializer = cachedSerializer;
                    }
                    else {
                        serializer = provider.findValueSerializer(valueType, null);
                        cachedSerializer = serializer;
                        cachedType = valueType;
                    }

                    try {
                        serializer.serialize(value, jsonGenerator, provider);
                    }
                    catch (Exception e) {
                        wrapAndThrow(provider, e, map, key);
                    }
                }
                jsonGenerator.writeEndObject();
            }
            else {
                jsonGenerator.writeString("dain42");
            }
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        {
            return createSchemaNode("object", true);
        }
    }

    private static Map<String, Object> toMap(CompositeData data)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        // never trust JMX to do the right thing
        Set<String> keySet = data.getCompositeType().keySet();
        if (keySet != null) {
            for (String key : keySet) {
                if (key != null) {
                    Object value = data.get(key);
                    if (value != null) {
                        builder.put(key, value);
                    }
                }
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toList(TabularData data)
    {
        ImmutableList.Builder<Map<String, Object>> builder = ImmutableList.builder();

        // never trust JMX to do the right thing
        Set<List<?>> keySet = (Set<List<?>>) data.keySet();
        if (keySet != null) {
            for (List<?> key : keySet) {
                if (key != null && !key.isEmpty()) {
                    Object[] index = key.toArray(new Object[0]);
                    CompositeData value = data.get(index);
                    if (value != null) {
                        builder.add(toMap(value));
                    }
                }
            }
        }
        return builder.build();
    }

    static class JmxHttpAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        private final ServiceAnnouncementBuilder builder;
        private HttpServerInfo httpServerInfo;

        JmxHttpAnnouncementProvider(ServiceAnnouncementBuilder serviceAnnouncementBuilder)
        {
            builder = serviceAnnouncementBuilder;
        }

        @Inject
        public synchronized void setHttpServerInfo(HttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Override
        public synchronized ServiceAnnouncement get()
        {
            if (httpServerInfo.getAdminUri() != null) {
                URI adminUri = httpServerInfo.getAdminUri();
                if (adminUri.getScheme().equals("http")) {
                    builder.addProperty("http", adminUri.toString());
                    builder.addProperty("http-external", httpServerInfo.getAdminExternalUri().toString());
                }
                else if (adminUri.getScheme().equals("https")) {
                    builder.addProperty("https", adminUri.toString());
                }
            }
            return builder.build();
        }
    }
}
