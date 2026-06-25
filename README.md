<!--
SPDX-FileCopyrightText: The LineageOS Project
SPDX-License-Identifier: Apache-2.0
-->

Updater
=======
Simple application to download and apply OTA packages.


Server requirements
-------------------
The app sends `GET` requests to the URL defined by the `updater_server_url`
resource (or the `lineage.updater.uri` system property) and expects as response
a JSON with the following structure:
```json
[
  {
    "datetime": 1781858358,
    "files": [
      {
        "filename": "ota-package.zip",
        "os_patch_level": "2026-06-01",
        "os_sdk_level": 36,
        "ota_property_files": "payload_metadata.bin:4662:187245,payload.bin:4662:1926274191,payload_properties.txt:1926278911:156,apex_info.pb:2220:1279,care_map.pb:3546:1069,metadata:69:683,metadata.pb:820:1352                        ",
        "sha256": "11468fc263696b8bc0afd35861c35d62a562ba29722447a3972c39f0023deb7f",
        "size": 1926282058,
        "url": "https://example.com/full/ota-package.zip"
      }
    ],
    "type": "nightly",
    "version": "23.2"
  }
]

```

The `datetime` attribute is the build date expressed as UNIX timestamp.  
The `filename` attribute is the name of the file to be downloaded.  
The `id` attribute is a string that uniquely identifies the update.  
The `romtype` attribute is the string to be compared with the `ro.lineage.releasetype` property.  
The `size` attribute is the size of the update expressed in bytes.  
The `url` attribute is the URL of the file to be downloaded.  
The `version` attribute is the string to be compared with the `ro.lineage.build.version` property.  

Additional attributes are ignored.


Build with Android Studio
-------------------------
Updater needs access to the system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. To do this:

 - Place this directory anywhere in the Android source tree
 - Generate a keystore and keystore.properties using `gen-keystore.sh`
 - Build the platform artifacts that provide the non-public classes used by
   the app. At minimum, the jars copied by `pull-system-libs.sh` must exist
   under `out/soong/.intermediates/`
 - Run `./pull-system-libs.sh` from this directory. By default it reads from
   `../../../out` relative to this repository path and will populate
   `system_libs/` with the jars Gradle expects:
   - `framework.jar`
   - `SettingsLib.jar`
   - `SpaLib.jar`
 - If your build output lives somewhere else, pass it explicitly:
   `./pull-system-libs.sh /path/to/out`

You need to do the above once, unless Android Studio can't find some symbol.
In that case, rebuild the relevant platform targets and rerun
`./pull-system-libs.sh`.
