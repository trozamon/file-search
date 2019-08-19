package com.alectenharmsel.indexer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.io.File;
import java.io.IOException;

class Config
{
    @JsonProperty
    private String server;

    @JsonProperty
    private List<Collection> collections;

    public static Config load(String fname) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(new File(fname), Config.class);
    }

    public String getServer() {
        return server;
    }

    public List<Collection> getCollections() {
        return collections;
    }

    public static class Collection {
        @JsonProperty
        private String name;

        @JsonProperty
        private String directory;

        public String getName() {
            return name;
        }

        public String getDirectory() {
            return directory;
        }
    }
}
