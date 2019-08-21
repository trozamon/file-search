package com.alectenharmsel.indexer;

import io.vertx.core.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

class ExceptionLogger implements Handler<Throwable> {
    private static final Logger log =
        Logger.getLogger(ExceptionLogger.class.getName());

    @Override
    public void handle(Throwable throwable) {
        log.log(Level.SEVERE, throwable, () -> "Unhandled exception");
    }
}
