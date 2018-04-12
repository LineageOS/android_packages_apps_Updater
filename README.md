Updater
=======
Simple application to download and apply OTA packages.


Server requirements
-------------------
The app sends `GET` requests to the URL defined by the `updater_server_url`
resource (or the `lineage.updater.uri` system property) and expects as response
a JSON with the following structure:
```json
{
  "response": [
    {
      "datetime": 1230764400,
      "filename": "ota-package.zip",
      "id": "5eb63bbbe01eeed093cb22bb8f5acdc3",
      "romtype": "nightly",
      "size": 314572800,
      "url": "https://example.com/ota-package.zip",
      "version": "15.1"
    }
  ]
}
```

The `datetime` attribute is the build date expressed as UNIX timestamp.  
The `filename` attribute is the name of the file to be downloaded.  
The `id` attribute is a string that uniquely identifies the update.  
The `romtype` attribute is the string to be compared with the `ro.lineage.releasetype` property.  
The `size` attribute is the size of the update expressed in bytes.  
The `url` attribute is the URL of the file to be downloaded.  
The `version` attribute is the string to be compared with the `ro.lineage.build.version` property.  

Additional attributes are ignored.


### Changelog

Updater will try to get the changelog only if the `updater_changelog_url`
resource is set. The app makes `GET` requests and expects a JSON as response.

The response must have the following format:
```json
{
  "last": 1230764400,
  "res": [
    {
      "project": "Project name",
      "subject": "Subject of the change",
      "submitted": 12307645300,
      "url": "https://example.com/change_url"
    }
  ]
}
```

The changes can be returned in batches. The `last` attribute reports
a timestamp value older than that of the oldest change returned. Its
value is used to generate the next `GET` request.

The format of the `GET` request is the following:
```http
GET /api/v1/device_name/timestamp
```

where `device_name` is the name of the device and `timestamp` is the value of
last received `last` attribute minus 1 or the current timestamp. The initial
request has a `timestamp` value of `-1`.


Build with Android Studio
-------------------------
Updater needs access to the system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. To do this:

 - Place this directory anywhere in the Android source tree
 - Generate a keystore and keystore.properties using `gen-keystore.sh`
 - Build the dependencies running `make UpdaterStudio` from the root of the
   Android source tree. This command will add the needed libraries in
   `system_libraries/`.

You need to do the above once, unless Android Studio can't find some symbol.
In this case, rebuild the system libraries with `make UpdaterStudio`.
