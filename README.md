**Server requirements**

The app sends `GET` requests to the URL defined by the `conf_update_server_url_def`
resource (or the `lineage.updater.uri` system property) and expects as response a
JSON with following structure:
```
[
    {
        "datetime": integer,
        "filename": string,
        "id": string,
        "romtype": string,
        "url": string,
        "version": string"
    },
    ...
]
```

The `datetime` attribute is the build date of the update.  
The `filename` attribute is the name of the file to be downloaded.  
The `id` attribute is a string that uniquely identifies the update.  
The `romtype` attribute is the string to be compared with the `ro.lineage.releasetype` property.  
The `url` attribute is the URL of the update to be downloaded.  
The `version` attribute is the string to be compared with the `ro.lineage.build.version` property.  
