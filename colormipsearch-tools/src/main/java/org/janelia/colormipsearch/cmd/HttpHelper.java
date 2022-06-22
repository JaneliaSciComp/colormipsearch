package org.janelia.colormipsearch.cmd;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.api.PartitionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HttpHelper.class);

    static Client createClient() {
        try {
            SSLContext sslContext = createSSLContext();

            JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            return ClientBuilder.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .sslContext(sslContext)
                    .hostnameVerifier((s, sslSession) -> true)
                    .register(jsonProvider)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static SSLContext createSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String authType) {
                            // Everyone is trusted
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) {
                            // Everyone is trusted
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            sslContext.init(null, trustManagers, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Error initilizing SSL context", e);
        }
    }

    static Invocation.Builder createRequestWithCredentials(Invocation.Builder requestBuilder, String credentials) {
        if (StringUtils.isNotBlank(credentials)) {
            return requestBuilder.header("Authorization", credentials);
        } else {
            return requestBuilder;
        }
    }

    /**
     * @param endpointSupplier Data endpoint supplier
     * @param chunkSize
     * @param names is a non empty set of item names to be retrieved
     * @param t data type reference
     * @param <T> data type
     * @return
     */
    static <T> Stream<T> retrieveDataStream(Supplier<WebTarget> endpointSupplier, String authorization, int chunkSize, Set<String> names, TypeReference<List<T>> t) {
        if (chunkSize > 0) {
            return PartitionUtils.partitionCollection(names, chunkSize).stream().parallel()
                    .flatMap(namesSubset -> {
                        LOG.info("Retrieve {} items", namesSubset.size());
                        return retrieveChunk(
                                endpointSupplier.get().queryParam("name", namesSubset.stream().reduce((s1, s2) -> s1 + "," + s2).orElse(null)),
                                authorization,
                                t).stream();
                    });
        } else {
            return retrieveChunk(
                    endpointSupplier.get().queryParam("name", CollectionUtils.isNotEmpty(names) ? names.stream().reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null),
                    authorization,
                    t).stream();
        }
    }

    static <T> List<T> retrieveChunk(WebTarget endpoint, String authorization, TypeReference<List<T>> t) {
        try (Response response = createRequestWithCredentials(endpoint.request(MediaType.APPLICATION_JSON), authorization).get()) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("Invalid response from " + endpoint.getUri() + " -> " + response);
            } else {
                return response.readEntity(new GenericType<>(t.getType()));
            }
        }
    }

}
