package com.alectenharmsel.indexer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class Main {
    public static final String SCHEDULING_CHANNEL = "scheduling";
    public static final String SHUTDOWN_CHANNEL = "shutdown";

    private static Main m = new Main();

    private Config conf;
    private Vertx vertx;
    private HttpServer server;
    private ExecutorService execs;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: file-search <start|stop> [path/to/config]");
            System.exit(1);
        }

        if ("stop".equals(args[0])) {
            m.stop();
        } else if (args.length == 2) {
            Config conf = null;

            try {
                conf = Config.load(args[1]);
            } catch (IOException ioe) {
                System.out.println("Could not open config");
                ioe.printStackTrace();
                System.exit(2);
            }

            m.conf = conf;
            m.run();
        } else {
            System.out.println("Usage: file-search <start|stop> [path/to/config]");
            System.exit(2);
        }
    }

    private void stop() {
        vertx.eventBus().publish(SHUTDOWN_CHANNEL, "");
        server.close();

        vertx.close(res -> {
            if (res.failed()) {
                System.out.println("Vertx could not closed");
                return;
            }

            execs.shutdown();

            try {
                execs.awaitTermination(48, TimeUnit.HOURS);
            } catch (InterruptedException ie) {
                System.out.println("Could not shut ExecutorService down");
            }

            System.out.println("ExecutorService shut down");
            System.exit(0);
        });
    }

    private void run() {
        vertx = Vertx.vertx();
        execs = Executors.newFixedThreadPool(1);

        vertx.exceptionHandler(new ExceptionLogger());

        server = vertx.createHttpServer()
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
