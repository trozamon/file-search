package com.alectenharmsel.indexer;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

final class ElasticUtil {
    private ElasticUtil() {
    }

    public static RestHighLevelClient createClient(Config conf) {
        URI uri;

        try {
            uri = new URI(conf.getServer());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));
        builder.setFailureListener(new RestClient.FailureListener() {
            @Override
            public void onFailure(Node node) {
                System.err.println("Failed");
            }
        });

        return new RestHighLevelClient(builder);
    }
}
