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
package com.proofpoint.bootstrap;

import com.google.common.collect.Lists;
import com.proofpoint.log.Logger;
import com.proofpoint.log.Logging;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Manages the startup and shutdown life cycle of the service.
 *
 * Maintains a list of managed instances. Calls annotated methods of those
 * instances as documented in the {@link #start()} and {@link #stop()}
 * methods.
 */
public final class LifeCycleManager
{
    private final Logger log = Logger.get(getClass());
    private final AtomicReference<State> state = new AtomicReference<>(State.LATENT);
    private final ConcurrentLinkedQueue<Object> managedInstances = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Object> acceptRequestInstances = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Object> stopTrafficInstances = new ConcurrentLinkedQueue<>();
    private final LifeCycleMethodsMap methodsMap;
    private final LifeCycleConfig config;
    private final CountDownLatch stoppedLatch = new CountDownLatch(1);

    private enum State
    {
        LATENT,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    /**
     * @param managedInstances initial list of objects that have life cycle annotations
     * @param methodsMap cache of mappings from class to annotated methods. Use null to get a default implementation.
     * @param config configuration
     * @throws Exception exceptions starting instances (depending on mode)
     */
    public LifeCycleManager(List<Object> managedInstances, @Nullable LifeCycleMethodsMap methodsMap, LifeCycleConfig config)
            throws Exception
    {
        this.methodsMap = (methodsMap != null) ? methodsMap : new LifeCycleMethodsMap();
        this.config = requireNonNull(config, "config is null");
        for (Object instance : managedInstances) {
            addInstance(instance);
        }
    }

    /**
     * @return the number of managed instances
     */
    public int size()
    {
        return managedInstances.size() + acceptRequestInstances.size() + stopTrafficInstances.size();
    }

    /**
     * Start the life cycle:
     * <ul>
     *     <li>Call the {@link AcceptRequests} method(s) of all managed instances.</li>
     *     <li>Install a shutdown hook to call {@link #stop()}.</li>
     * </ul>
     *
     * @throws Exception errors
     */
    public void start()
            throws Exception
    {
        if (!state.compareAndSet(State.LATENT, State.STARTING)) {
            throw new Exception("System already starting");
        }
        log.info("Life cycle starting...");

        for (Object obj : acceptRequestInstances) {
            LifeCycleMethods methods = methodsMap.get(obj.getClass());
            startInstance(obj, methods.methodsFor(AcceptRequests.class));
            if (!methods.hasFor(PreDestroy.class)) {
                acceptRequestInstances.remove(obj);   // remove reference to instances that aren't needed anymore
            }
        }
        for (Object obj : managedInstances) {
            LifeCycleMethods methods = methodsMap.get(obj.getClass());
            if (!methods.hasFor(PreDestroy.class)) {
                managedInstances.remove(obj);   // remove reference to instances that aren't needed anymore
            }
        }

        Thread thread = new Thread(() -> {
            try {
                LifeCycleManager.this.stop();
            }
            catch (Exception e) {
                log.error(e, "Trying to shut down");
            }
        });
        Runtime.getRuntime().addShutdownHook(thread);
        Logging.addShutdownLatchToWaitFor(stoppedLatch);

        state.set(State.STARTED);
        log.info("Life cycle startup complete. System ready.");
    }

    /**
     * Stop the life cycle:
     * <ul>
     *     <li>Call the {@link StopTraffic} method(s) of all managed instances.</li>
     *     <li>Wait the configured stop traffic delay.</li>
     *     <li>Call in reverse order the {@link PreDestroy} method(s) of all managed instances with an {@link AcceptRequests} method.</li>
     *     <li>Call in reverse order the {@link PreDestroy} method(s) of all other managed instances.</li>
     * </ul>
     *
     * @throws Exception errors
     */
    public void stop()
            throws Exception
    {
        if (!state.compareAndSet(State.STARTED, State.STOPPING)) {
            return;
        }

        log.info("Life cycle stopping...");
        stopList(stopTrafficInstances, StopTraffic.class);

        log.info("Life cycle unannounced...");
        long stopTrafficDelay = config.getStopTrafficDelay().toMillis();
        if (stopTrafficDelay != 0) {
            Thread.sleep(stopTrafficDelay);
        }

        stopList(acceptRequestInstances, PreDestroy.class);

        log.info("Life cycle stopped accepting new requests...");
        stopList(managedInstances, PreDestroy.class);

        state.set(State.STOPPED);
        log.info("Life cycle stopped.");

        stoppedLatch.countDown();
    }

    private void stopList(Queue<Object> instances, Class<? extends Annotation> annotation)
    {
        List<Object> reversedInstances = Lists.newArrayList(instances);
        Collections.reverse(reversedInstances);

        for (Object obj : reversedInstances) {
            log.debug("Stopping %s", obj.getClass().getName());
            LifeCycleMethods methods = methodsMap.get(obj.getClass());
            for (Method preDestroy : methods.methodsFor(annotation)) {
                log.debug("\t%s()", preDestroy.getName());
                try {
                    preDestroy.invoke(obj);
                }
                catch (Exception e) {
                    log.error(e, "Stopping %s.%s() failed:", obj.getClass().getName(), preDestroy.getName());
                }
            }
        }
    }

    /**
     * Add an additional managed instance and call its {@link PostConstruct} method(s).
     *
     * @param instance instance to add
     * @throws Exception errors
     */
    public void addInstance(Object instance)
            throws Exception
    {
        State currentState = state.get();
        if ((currentState == State.STOPPING) || (currentState == State.STOPPED)) {
            throw new IllegalStateException();
        }
        else {
            LifeCycleMethods methods = methodsMap.get(instance.getClass());
            startInstance(instance, methods.methodsFor(PostConstruct.class));
            if (methods.hasFor(AcceptRequests.class)) {
                acceptRequestInstances.add(instance);
            }
            else if (methods.hasFor(PreDestroy.class)) {
                managedInstances.add(instance);
            }
            if (methods.hasFor(StopTraffic.class)) {
                stopTrafficInstances.add(instance);
            }
        }
    }

    private void startInstance(Object obj, Collection<Method> methods)
            throws IllegalAccessException, InvocationTargetException
    {
        log.debug("Starting %s", obj.getClass().getName());
        for (Method method : methods) {
            log.debug("\t%s()", method.getName());
            method.invoke(obj);
        }
    }
}
