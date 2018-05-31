package com.proofpoint.jaxrs;

import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.proofpoint.log.Logger;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/admin/log/")
public class LoggingResource {
    private static final Logger log = Logger.get(LoggingResource.class);
    private final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    private final String stringClassName = String.class.getName();
    private ObjectName logBeanName;

    public LoggingResource() {
        try {
            logBeanName = new ObjectName("com.proofpoint.log:name=Logging");
        } catch (MalformedObjectNameException e) {
            log.error("This should never happen.  Admin port access to logging is disabled.");
            logBeanName = null;
        }
    }

    @Path("level/{logName}")
    @GET
    @Produces(TEXT_PLAIN)
    public Response getLogLevel(@PathParam("logName") @DefaultValue("") String logName) {
        try {
            if (logBeanName != null) {
                String[] types = {stringClassName};
                Object[] params = {logName};
                String level = (String) mbs.invoke(logBeanName,"getLevel", params, types);
                String msg = String.format("The logging level for %s is %s", logName, level);
                log.debug(msg);
                return Response.ok().entity(msg).build();
            } else {
                String msg = "Admin port support for logging levels is disabled";
                log.info(msg);
                return Response.serverError().entity(msg).build();
            }
        } catch (InstanceNotFoundException e) {
            log.error("Instance not found", e);
            return Response.serverError().entity("Bean not found").build();
        } catch (ReflectionException e) {
            log.error("Reflection", e);
            return Response.serverError().entity("Reflection error").build();
        } catch (MBeanException e) {
            log.error("Mbean", e);
            return Response.serverError().entity("MBean error").build();
        }
    }

    @Path("level")
    @PUT
    @Produces(TEXT_PLAIN)
    public Response setLogLevel(@QueryParam("logName") @DefaultValue("") String logName, @QueryParam("logLevel")
    @DefaultValue("") String logLevel) {
        log.debug("Setting log %s to %s", logName, logLevel);
        try {
            if (logBeanName != null) {
                String[] types = {stringClassName, stringClassName};
                Object[] params = {logName, logLevel};
                mbs.invoke(logBeanName, "setLevel", params, types);
                String msg = String.format("Log level for %s set to %s", logName, logLevel);
                log.debug(msg);
                return Response.ok().entity(msg).build();
            } else {
                String msg = "Admin port support for logging levels is disabled";
                log.info(msg);
                return Response.serverError().entity(msg).build();
            }
        } catch (InstanceNotFoundException e) {
            log.error(e, "Instance not found");
            return Response.serverError().entity("Bean not found").build();
        } catch (ReflectionException e) {
            log.error(e, "Reflection");
            return Response.serverError().entity("Reflection error").build();
        } catch (MBeanException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof java.lang.IllegalArgumentException) {
                    String msg = cause.getMessage();
                    log.error(e, "Invalid level");
                    return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).entity(msg).build();
                } else {
                    log.error(e, "Mbean");
                    return Response.serverError().entity("MBean error").build();
                }
            } else {
                log.error(e, "Mbean");
                return Response.serverError().entity("MBean error").build();
            }
        }
    }
}
