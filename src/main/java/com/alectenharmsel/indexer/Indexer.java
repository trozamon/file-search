package com.alectenharmsel.indexer;

import com.google.common.hash.Hashing;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

class Indexer implements Runnable {
    // 12 hour delay between indexing
    public static final int REST = 12 * 60 * 60 * 1000;

    private Vertx vertx;
    private Config.Index index;
    private Tika tika;
    private RestHighLevelClient client;

    public Indexer(Vertx vertx, Config.Index index) {
        this.vertx = vertx;
        this.index = index;
        this.tika = new Tika();

        RestClientBuilder builder =
            RestClient.builder(new HttpHost("127.0.0.1", 9200, "http"));
        builder.setFailureListener(new RestClient.FailureListener() {
            @Override
            public void onFailure(Node node) {
                System.err.println("Failed");
            }
        });

        this.client = new RestHighLevelClient(builder);
    }

    @Override
    public void run() {
        try {
            walk();
        } catch (Exception re) {
            System.out.println("Exception here");
            re.printStackTrace();
        }
    }

    private void walk() {
        Path root = Paths.get(index.getDirectory())
            .toAbsolutePath()
            .normalize();
        int depth = Integer.MAX_VALUE;

        try {
            boolean resp = client.ping(RequestOptions.DEFAULT);
            if (resp) {
                System.out.println("Good!");
            } else {
                System.out.println("Bad!");
            }

            MainResponse response = client.info(RequestOptions.DEFAULT);
            System.out.println(response.getClusterUuid());
            System.out.println(response.getNodeName());

            GetIndexRequest req = new GetIndexRequest();
            req.indices(index.getActualName());

            boolean exists = client.indices().exists(req, RequestOptions.DEFAULT);

            if (!exists) {
                System.out.println("Does not exist, creating...");
                CreateIndexRequest request = new CreateIndexRequest(index.getActualName()); 
                CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
                boolean acknowledged = createIndexResponse.isAcknowledged();

                System.out.println("acknowledged: " + acknowledged);
            } else {
                System.out.println("Already existed...");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        try {
            Files
                .find(root, depth, (path, attrs) -> attrs.isRegularFile())
                .forEach(path -> indexPath(path));
        } catch (IOException ioe) {
            System.out.println("Can't walk " + index.getDirectory());
            ioe.printStackTrace();
        }

        try {
            this.client.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        vertx.setTimer(REST, otherId -> {
            vertx.eventBus().publish(Main.SCHEDULING_CHANNEL, index.getName());
        });
    }

    private void indexPath(Path path) {
        try {
            String content = tika.parseToString(path);
            String filename = Base64.getUrlEncoder()
                .encodeToString(path.toString().getBytes());
            String id = Hashing.sha256()
                .hashString(path.toString(), StandardCharsets.UTF_8)
                .toString();

            System.out.println("Indexing " + path.toString());

            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("filename", filename);
            jsonMap.put("content", content);

            IndexRequest request = new IndexRequest(index.getActualName())
                .id(id)
                .source(jsonMap);

            IndexResponse resp =
                client.index(request, RequestOptions.DEFAULT);

            if (resp.getResult() == DocWriteResponse.Result.CREATED) {
                System.out.println("Created");
            } else if (resp.getResult() == DocWriteResponse.Result.UPDATED) {
                System.out.println("Updated");
            } else {
                System.out.println("Other");
            }
        } catch (IOException ioe) {
            System.err.println("Error reading file " + path.toString());
            ioe.printStackTrace();
        } catch (TikaException te) {
            System.out.println("Not indexing file " + path.toString());
        }
    }
}
