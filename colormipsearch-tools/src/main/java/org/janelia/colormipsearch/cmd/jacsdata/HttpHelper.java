package org.janelia.colormipsearch.cmd.jacsdata;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
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
import org.janelia.colormipsearch.results.ItemsHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HttpHelper.class);

    public static Client createClient() {
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

    public static Invocation.Builder createRequestWithCredentials(Invocation.Builder requestBuilder, String credentials) {
        if (StringUtils.isNotBlank(credentials)) {
            return requestBuilder.header("Authorization", credentials);
        } else {
            return requestBuilder;
        }
    }

    /**
     * @param endpointSupplier Data endpoint supplier
     * @param chunkSize
     * @param fieldName query field name
     * @param fieldValues is a non empty set of item values to be retrieved
     * @param t data type reference
     * @param <T> data type
     * @return
     */
    public static <T> Stream<T> retrieveDataStreamForFieldValues(Supplier<WebTarget> endpointSupplier,
                                                                 String authorization,
                                                                 int chunkSize,
                                                                 String fieldName,
                                                                 Set<String> fieldValues,
                                                                 TypeReference<List<T>> t) {
        if (chunkSize > 0) {
            return ItemsHandling.partitionCollection(fieldValues, chunkSize).entrySet().stream()
                    .flatMap(indexedValuesSubset -> {
                        LOG.debug("Retrieve {}:{} items", indexedValuesSubset.getKey(), indexedValuesSubset.getValue().size());
                        List<T> subsetResults = retrieveData(
                                endpointSupplier.get().queryParam(fieldName, indexedValuesSubset.getValue().stream().reduce((s1, s2) -> s1 + "," + s2).orElse(null)),
                                authorization,
                                t,
                                Collections.emptyList());
                        return subsetResults.stream();
                    });
        } else {
            List<T> results = retrieveData(
                    endpointSupplier.get().queryParam(fieldName, CollectionUtils.isNotEmpty(fieldValues) ? fieldValues.stream().reduce((s1, s2) -> s1 + "," + s2).orElse(null) : null),
                    authorization,
                    t,
                    Collections.emptyList());
            return results.stream();
        }
    }

    public static <T> T retrieveData(WebTarget endpoint, String authorization, TypeReference<T> t, T resultOnError) {
        LOG.debug("Retrieve data from {}", endpoint);
        try (Response response = createRequestWithCredentials(endpoint.request(MediaType.APPLICATION_JSON), authorization).get()) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                LOG.error("Invalid response from {}: {}", endpoint.getUri(), response);
                return resultOnError;
            } else {
                return response.readEntity(new GenericType<>(t.getType()));
            }
        }
    }

}
