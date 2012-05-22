package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ServiceInventory
{
    private static final Logger log = Logger.get(ServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final Duration updateInterval;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorListRepresentation> serviceDescriptorListRepresentationCodec;
    private final HttpClient httpClient;
    private final Validator validator;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<List<ServiceDescriptor>>(ImmutableList.<ServiceDescriptor>of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("service-inventory-%s").setDaemon(true).build());
    private ScheduledFuture<?> scheduledFuture;

    @Inject
    public ServiceInventory(ServiceInventoryConfig config,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorListRepresentation> serviceDescriptorListRepresentationCodec,
            @ForDiscoveryClient HttpClient httpClient)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(serviceDescriptorListRepresentationCodec, "serviceDescriptorListRepresentationCodec is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.serviceInventoryUri = config.getServiceInventoryUri();
        updateInterval = config.getUpdateInterval();
        this.serviceDescriptorListRepresentationCodec = serviceDescriptorListRepresentationCodec;
        this.httpClient = httpClient;
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();

        if (serviceInventoryUri != null) {
            String scheme = serviceInventoryUri.getScheme().toLowerCase();
            Preconditions.checkArgument(scheme.equals("http") || scheme.equals("https") || scheme.equals("file"), "Service inventory uri must have a http, https, or file scheme");

            updateServiceInventory(false);
            log.info("Loaded service inventory");
        }
    }

    @PostConstruct
    public synchronized void start()
    {
        if (serviceInventoryUri == null || scheduledFuture != null) {
            return;
        }
        scheduledFuture = executorService.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    updateServiceInventory(true);
                }
                catch (Throwable e) {
                    log.error(e, "Unexpected exception from service inventory update");
                }
            }
        }, (long) updateInterval.toMillis(), (long) updateInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public synchronized void stop()
    {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors.get();
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type)
    {
        return Iterables.filter(getServiceDescriptors(), new Predicate<ServiceDescriptor>()
        {
            @Override
            public boolean apply(ServiceDescriptor serviceDescriptor)
            {
                return serviceDescriptor.getType().equals(type);
            }
        });
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type, final String pool)
    {
        return Iterables.filter(getServiceDescriptors(), new Predicate<ServiceDescriptor>()
        {
            @Override
            public boolean apply(ServiceDescriptor serviceDescriptor)
            {
                return serviceDescriptor.getType().equals(type) &&
                        serviceDescriptor.getPool().equals(pool);
            }
        });
    }

    @Managed
    public void updateServiceInventory(boolean quiet)
    {
        if (serviceInventoryUri == null) {
            return;
        }

        ServiceDescriptorListRepresentation serviceDescriptorListRepresentation;
        try {
            if (serviceInventoryUri.getScheme().toLowerCase().startsWith("http")) {
                Builder requestBuilder = prepareGet()
                        .setUri(serviceInventoryUri)
                        .setHeader("User-Agent", nodeInfo.getNodeId());
                serviceDescriptorListRepresentation = httpClient.execute(requestBuilder.build(),
                        createJsonResponseHandler(serviceDescriptorListRepresentationCodec));
            }
            else {
                File file = new File(serviceInventoryUri);
                String json = Files.toString(file, Charsets.UTF_8);
                serviceDescriptorListRepresentation = serviceDescriptorListRepresentationCodec.fromJson(json);
            }
        }
        catch (Exception e) {
            String message = "Error loading service inventory from " + serviceInventoryUri.toASCIIString();
            log.error(message);
            if (quiet) {
                return;
            }
            else {
                throw new RuntimeException(message);
            }
        }

        List<ServiceDescriptorRepresentation> descriptors = null;
        ImmutableList.Builder<String> violationMessagesBuilder = ImmutableList.builder();

        Set<ConstraintViolation<ServiceDescriptorListRepresentation>> listViolations = validator.validate(serviceDescriptorListRepresentation);
        if (listViolations.size() > 0) {
            for (ConstraintViolation<ServiceDescriptorListRepresentation> violation : listViolations) {
                log.error(violation.getMessage());
                violationMessagesBuilder.add(violation.getMessage());
            }
        }
        else {
            if (!environment.equals(serviceDescriptorListRepresentation.getEnvironment())) {
                String message = String.format("Expected service inventory environment to be %s, but was %s", environment,
                        serviceDescriptorListRepresentation.getEnvironment());
                log.error(message);
                violationMessagesBuilder.add(message);
            }

            descriptors = newArrayList(serviceDescriptorListRepresentation.getServiceDescriptorRepresentations());
            for (ServiceDescriptorRepresentation descriptor : descriptors) {
                Set<ConstraintViolation<ServiceDescriptorRepresentation>> violations = validator.validate(descriptor);
                if (violations.size() > 0) {
                    for (ConstraintViolation<ServiceDescriptorRepresentation> violation : violations) {
                        String message = String.format("%s %s", violation.getMessage(), descriptor);
                        log.error(message);
                        violationMessagesBuilder.add(message);
                    }
                }
            }
        }

        List<String> violationMessages = violationMessagesBuilder.build();
        if (violationMessages.size() == 0) {
            Collections.shuffle(descriptors);
            serviceDescriptors.set(serviceDescriptorListRepresentation.getServiceDescriptors());
            log.info("Updated service inventory");
        }
        else if (!quiet) {
            throw new RuntimeException(String.format("Invalid service inventory from %s %s",
                    serviceInventoryUri.toASCIIString(), violationMessages));
        }
    }
}
