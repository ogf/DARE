package es.uvigo.ei.sing.dare.client;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.Validate;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

public class URIPoller {

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private final Client client;
    private final MediaType requestedType;

    private final int pollingIntervalMilliseconds;

    public URIPoller(Client client) {
        this(client, MediaType.APPLICATION_XML_TYPE);
    }

    public URIPoller(Client client, MediaType defaultRequestedType) {
        this(client, defaultRequestedType, 1000);
    }

    public URIPoller(Client client, MediaType defaultRequestedType,
            int pollingIntervalMilliseconds) {
        Validate.notNull(client);
        Validate.notNull(defaultRequestedType);
        this.client = client;
        this.requestedType = defaultRequestedType;
        this.pollingIntervalMilliseconds = pollingIntervalMilliseconds;
    }

    public <T> Future<T> async(URI uriToExecution, Class<T> resultType) {
        return async(uriToExecution, resultType, requestedType);
    }

    public <T> Future<T> async(final URI uriToExecution,
            final Class<T> resultType,
            final MediaType acceptedType) {
        Validate.notNull(resultType);
        Validate.notNull(uriToExecution);
        Validate.notNull(acceptedType);
        return executor.submit(new Callable<T>() {

            @Override
            public T call() throws Exception {
                ClientResponse clientResponse = doGet(uriToExecution,
                        acceptedType);
                while (clientResponse.getStatus() == Status.NO_CONTENT
                        .getStatusCode()) {
                    Thread.sleep(pollingIntervalMilliseconds);
                    clientResponse = doGet(uriToExecution, acceptedType);
                }
                if (resultType.equals(ClientResponse.class)) {
                    return resultType.cast(clientResponse);
                }
                return clientResponse.getEntity(resultType);
            }

            private ClientResponse doGet(final URI uriToExecution,
                    final MediaType acceptedType) {
                return client.resource(uriToExecution)
                        .accept(acceptedType).get(ClientResponse.class);
            }
        });
    }

    public <T> T retrieve(URI uriToExecution, Class<T> resultType) {
        return retrieve(uriToExecution, resultType, this.requestedType);
    }

    public <T> T retrieve(URI uriToExecution, Class<T> resultType,
            MediaType requestedType) {
        Future<T> async = async(uriToExecution, resultType, requestedType);
        try {
            return async.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

}
