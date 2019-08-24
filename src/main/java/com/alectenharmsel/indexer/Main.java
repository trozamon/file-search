package com.alectenharmsel.indexer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Main {
    public static final String SCHEDULING_CHANNEL = "scheduling";

    private Config conf;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: file-search <path/to/config>");
            System.exit(1);
        }

        Config conf = null;

        try {
            conf = Config.load(args[0]);
        } catch (IOException ioe) {
            System.out.println("Could not open config");
            ioe.printStackTrace();
            System.exit(2);
        }

        new Main(conf).run();
    }

    public Main(Config conf) {
        this.conf = conf;
    }

    private void run() {
        Vertx vertx = Vertx.vertx();
        ExecutorService execs = Executors.newFixedThreadPool(1);

        vertx.exceptionHandler(new ExceptionLogger());

        HttpServer server = vertx.createHttpServer()
            .exceptionHandler(new ExceptionLogger());
        Router router = Router.router(vertx)
            .exceptionHandler(new ExceptionLogger());

        router.route("/static/*")
            .method(HttpMethod.GET)
            .handler(StaticHandler.create("webroot"));
        router.route("/")
            .method(HttpMethod.GET)
            .blockingHandler(new SearchHandler(vertx, conf));

        server.requestHandler(router).listen(8080);

        vertx.eventBus().consumer(SCHEDULING_CHANNEL, message -> {
            String indexName = message.body().toString();

            System.out.println("Scheduling " + indexName);
            execs.submit(new Indexer(vertx, conf, getIndex(indexName)));
        });

        for (Config.Index idx : conf.getIndices()) {
            execs.submit(new Indexer(vertx, conf, idx));
        }
    }

    private Config.Index getIndex(String name) {
        for (Config.Index idx : conf.getIndices()) {
            if (name.equals(idx.getName())) {
                return idx;
            }
        }

        return null;
    }
}
