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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.MainResponse;

class Indexer implements Runnable {

    // 12 hour delay between indexing
    public static final int REST = 12 * 60 * 60 * 1000;

    private static final Logger log =
        Logger.getLogger(Indexer.class.getName());

    private Vertx vertx;
    private Config.Index index;
    private Tika tika;
    private RestHighLevelClient client;
    private boolean running;

    public Indexer(Vertx vertx, Config conf, Config.Index index) {
        this.running = true;
        this.vertx = vertx;
        this.index = index;
        this.tika = new Tika();
        this.client = ElasticUtil.createClient(conf);
    }

    @Override
    public void run() {
        vertx.eventBus().consumer(Main.SHUTDOWN_CHANNEL, msg -> {
            this.running = false;
        });

        try {
            walk();
        } catch (Exception re) {
            log.log(Level.SEVERE, re, () -> "Exception in main indexing loop");
            re.printStackTrace();
        }
    }

    private void walk() {
        Path root = Paths.get(index.getDirectory())
            .toAbsolutePath()
            .normalize();
        int depth = Integer.MAX_VALUE;

        try {
            GetIndexRequest req = new GetIndexRequest();
            req.indices(index.getActualName());

            boolean exists = client.indices().exists(req, RequestOptions.DEFAULT);

            if (!exists) {
                log.info(() -> {
                    return "Index " + index.getActualName() +
                        " does not exist, creating...";
                });

                CreateIndexRequest request = new CreateIndexRequest(index.getActualName()); 
                CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            } else {
                log.info(() -> "Index " + index.getActualName() + " exists");
            }
        } catch (IOException ioe) {
            log.log(Level.SEVERE, ioe, () -> "Error with ElasticSearch");
            return;
        }

        try {
            Files
                .find(root, depth, (path, attrs) -> attrs.isRegularFile())
                .forEach(path -> indexPath(path));
        } catch (NeedToStop nts) {
            log.info(() -> "Quit indexing " + index.getActualName());
            return;
        } catch (IOException ioe) {
            log.log(Level.SEVERE, ioe,
                    () -> "Error walking" + index.getDirectory());
        }

        try {
            this.client.close();
        } catch (IOException ioe) {
            log.log(Level.WARNING, ioe,
                    () -> "Could not close ElasticSearch client");
        }

        log.info(() -> "Done indexing " + index.getActualName());

        vertx.setTimer(REST, otherId -> {
            vertx.eventBus().publish(Main.SCHEDULING_CHANNEL, index.getName());
        });
    }

    private void indexPath(Path path) {
        if (!running) {
            throw new NeedToStop();
        }

        try {
            String content = tika.parseToString(path);
            String filename = Base64.getUrlEncoder()
                .encodeToString(path.toString().getBytes());
            String id = Hashing.sha256()
                .hashString(path.toString(), StandardCharsets.UTF_8)
                .toString();

            log.finest(() -> "Indexing " + path.toString());

            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("filename", filename);
            jsonMap.put("content", content);

            IndexRequest request = new IndexRequest(index.getActualName())
                .id(id)
                .source(jsonMap);

            IndexResponse resp =
                client.index(request, RequestOptions.DEFAULT);

            if (resp.getResult() == DocWriteResponse.Result.CREATED ||
                    resp.getResult() == DocWriteResponse.Result.UPDATED) {
                log.finest(() -> "Indexed " + path.toString());
            } else {
                log.finest(() -> "Got other response for " + path.toString());
            }
        } catch (IOException ioe) {
            log.log(Level.SEVERE, ioe,
                    () -> "Error reading file " + path.toString());
            ioe.printStackTrace();
        } catch (TikaException te) {
            log.finest(() -> "Not indexing " + path.toString());
        }
    }

    private static class NeedToStop extends RuntimeException {
    }
}
