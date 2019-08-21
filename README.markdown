# File Search Engine

Simple little search engine that indexes files and displays a little web
interface to search them.

NOTE: still extremely in-progress.

## Running

    ./gradlew build
    java -jar file-search.jar <path/to/config>

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
