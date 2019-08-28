# File Search Engine

Simple little search engine that indexes files and displays a little web
interface to search them.

NOTE: still extremely in-progress.

## Running

    # development
    yarn install
    docker-compose up -d # for ElasticSearch
    ./gradlew run

    # production
    yarn install
    ./gradlew build
    cd build/distributions
    unzip file-search.zip
    cd file-search
    bin/file-search ../../../example.config

Config files are JSON and look like:

    {
      "server": "http://elastic.example.com:9200",
      "indices": [
        {
          "name": "Logs",
          "directory": "/var/log"
        }
      ]
    }
