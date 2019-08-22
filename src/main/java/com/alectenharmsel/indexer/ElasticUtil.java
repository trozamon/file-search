package com.alectenharmsel.indexer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

final class ElasticUtil {

    private static final Logger log =
        Logger.getLogger(ElasticUtil.class.getName());

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
                log.severe("ElasticSearch node failed");
            }
        });

        return new RestHighLevelClient(builder);
    }
}
