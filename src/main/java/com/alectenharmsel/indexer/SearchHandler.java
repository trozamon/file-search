package com.alectenharmsel.indexer;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;

class SearchHandler implements Handler<RoutingContext> {

    public static final int SIZE = 20;

    private static final Logger log =
        Logger.getLogger(SearchHandler.class.getName());

    private Config conf;
    private RestHighLevelClient client;
    private JadeTemplateEngine jade;

    public SearchHandler(Vertx vertx, Config conf) {
        this.conf = conf;
        this.client = ElasticUtil.createClient(conf);
        this.jade = JadeTemplateEngine.create(vertx);
    }

    @Override
    public void handle(RoutingContext ctx) {
        log.info(() -> "Responding to " + ctx.request().uri());

        HttpServerResponse resp = ctx.response();

        String q = first(ctx.queryParam("q"));
        String idx = first(ctx.queryParam("idx"));
        int page = 1;
        try {
            String tmp = first(ctx.queryParam("page"));
            if (null != tmp) {
                page = Integer.parseInt(tmp);
            }
        } catch (NumberFormatException nfe) {
            log.log(Level.WARNING, nfe, () -> "Could not parse page");
        }

        if (null == idx && !conf.getIndices().isEmpty()) {
            idx = conf.getIndices().get(0).getName();
        }

        Map<String, Object> renderContext = new HashMap<>();

        renderContext.put("q", q);
        renderContext.put("idx", idx);
        renderContext.put("indices", conf.getIndices());
        renderContext.put("page", page);

        if (null != q && null != idx) {
            SearchResult result = search(idx, q, page);
            renderContext.put("filenames", result.filenames);

            float total = (float) result.total;
            total /= (float) SIZE;
            int totalPages = (int) Math.ceil(total);

            renderContext.put("totalPages", totalPages);
            renderContext.put("prevPage", Math.max(page - 1, 1));
            renderContext.put("nextPage", Math.min(page + 1, totalPages));
        }

        jade.render(renderContext, "index", res -> {
            if (res.succeeded()) {
                log.fine(() -> "Rendered index");
                resp.putHeader("Content-Type", "text/html");
                resp.end(res.result());
            } else {
                log.log(Level.SEVERE, "Error rendering index", res.cause());
                resp.putHeader("Content-Type", "text/plain");
                resp.end("Sorry, there was an error processing your request");
            }
        });
    }

    private SearchResult search(String idx, String q, int page) {
        SearchRequest req = new SearchRequest(idx); 
        SearchSourceBuilder source = new SearchSourceBuilder(); 

        source.query(QueryBuilders.queryStringQuery(q).field("content"));
        source.from((page - 1) * SIZE);
        source.size(SIZE);
        source.timeout(new TimeValue(10, TimeUnit.SECONDS));
        source.fetchSource(true);

        req.source(source);

        SearchResult result = new SearchResult();

        try {
            SearchResponse resp = client.search(req, RequestOptions.DEFAULT);
            SearchHits hits = resp.getHits();
            long total = hits.getTotalHits().value;

            result.total = total;

            for (SearchHit hit : hits.getHits()) {
                Map<String, Object> body = hit.getSourceAsMap();

                String fname = (String) body.get("filename");
                String decoded = new String(
                        Base64.getUrlDecoder().decode(fname));

                Optional<Config.Index> index = conf.getIndices().stream()
                    .filter(i -> i.getActualName().equals(idx))
                    .findFirst();

                if (index.isPresent()) {
                    String root = Paths.get(index.get().getDirectory())
                        .toAbsolutePath()
                        .normalize()
                        .toString();

                    decoded = decoded.substring(root.length() + 1);
                }

                result.filenames.add(decoded);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return result;
    }

    private String first(List<String> strings) {
        if (null != strings && !strings.isEmpty()) {
            return strings.get(0);
        }

        return null;
    }

    private static class SearchResult {
        List<String> filenames;
        long total;

        public SearchResult() {
            filenames = new ArrayList<>();
            total = 0;
        }
    }
}
