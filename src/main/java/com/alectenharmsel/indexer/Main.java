package com.alectenharmsel.indexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: solr-indexer <path/to/config>");
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

        run(conf);
    }

    private static void run(Config conf) {
        System.out.println("POSTing to " + conf.getServer());

        for (Config.Collection coll : conf.getCollections()) {
            try {
                Files.find(
                        Paths.get(coll.getDirectory()),
                        Integer.MAX_VALUE,
                        (path, attrs) -> {
                            return attrs.isRegularFile();
                        })
                    .forEach(path -> {
                        visit(path);
                    });
            } catch (IOException ioe) {
                System.out.println("Can't walk " + coll.getDirectory());
                ioe.printStackTrace();
            }
        }
    }

    private static void visit(Path path) {
        Tika tika = new Tika();

        try {
            String res = tika.parseToString(path);

            if (null != res) {
                System.out.println("Indexing " + path.toString());
            }
        } catch (IOException ioe) {
            System.err.println("Error reading file " + path.toString());
            ioe.printStackTrace();
        } catch (TikaException te) {
            System.out.println("Not indexing file " + path.toString());
        }
    }
}
