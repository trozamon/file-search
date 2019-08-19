# Solr Indexer

Very simple little indexer for Solr.

## Running

    java -jar solr-indexer.jar <path/to/config>

Config files are JSON and look like:

    {
      "server": "http://solr.example.com:8983",
      "collections": [
        {
          "name": "my_collection",
          "directory": "/var/log"
        }
      ]
    }
