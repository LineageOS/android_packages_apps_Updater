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
    "date": "2026-06-19",
    "datetime": 1781858358,
    "files": [
      {
        "date": "2026-06-19",
        "datetime": 1781858358,
        "filename": "ota-package.zip",
        "filepath": "/full/ota-package.zip",
        "os_patch_level": "2026-06-01",
        "os_sdk_level": 36,
        "ota_property_files": "payload_metadata.bin:4662:187245,payload.bin:4662:1926274191,payload_properties.txt:1926278911:156,apex_info.pb:2220:1279,care_map.pb:3546:1069,metadata:69:683,metadata.pb:820:1352                        ",
        "sha1": "bf906d8730f23b3236977ddfb48f0a234652fdd6",
        "sha256": "11468fc263696b8bc0afd35861c35d62a562ba29722447a3972c39f0023deb7f",
        "size": 1926282058,
        "type": "nightly",
        "url": "https://example.com/full/ota-package.zip"
      },
      {
        "filename": "boot.img",
        "filepath": "/full/boot.img",
        "sha1": "03ed1012dc32f1a735af7f4fbe3cfc38c4ea0751",
        "sha256": "f27f4a491f1e017a043855aa78021f899edccc2c281edb9098a5226e4dafe799",
        "size": 201326592,
        "url": "https://example.com/full/boot.img"
      },
      {
        "filename": "dtbo.img",
        "filepath": "/full/dtbo.img",
        "sha1": "496f3692375a66abe86e6a5e8a2bb7927e7eab87",
        "sha256": "fc4111342bbe78e8c572dd47372c97bfc3f7b86fbdc0b8233eaf302c85dcdb05",
        "size": 25165824,
        "url": "https://example.com/full/dtbo.img"
      },
      {
        "filename": "recovery.img",
        "filepath": "/full/recovery.img",
        "sha1": "9ca26b38a3a4f9f1fe34a06bce0253c052b4a496",
        "sha256": "9fea55e5707f8a886aeed404aed0a8e331600fe0ffa4b6d660be1d6a758aa8e0",
        "size": 104857600,
        "url": "https://example.com/full/recovery.img"
      },
      {
        "filename": "super_empty.img",
        "filepath": "/full/super_empty.img",
        "sha1": "26a3bb85fb968f2464dda48b1ada4b31ce591bff",
        "sha256": "65b580a751bab05f7b69d3859a6a88dac3426a7f91ad1209924c5ec92a5706d3",
        "size": 5184,
        "url": "https://example.com/full/super_empty.img"
      },
      {
        "filename": "vbmeta.img",
        "filepath": "/full/vbmeta.img",
        "sha1": "c2e83b37a5b1cf13bcb17edf027c35eebd34732f",
        "sha256": "7ebe9f43eea950a49c02d6c924c1268e971e7fda0f605553f3005c1fbbf226fb",
        "size": 8192,
        "url": "https://example.com/full/vbmeta.img"
      },
      {
        "filename": "vendor_boot.img",
        "filepath": "/full/vendor_boot.img",
        "sha1": "6c0a677e1f1b822e83847226061a4a045f35b3e1",
        "sha256": "381169cb3413793b7bf7c2452f90a2897f6aa2aacfd6b4dfcd40863f217d8a39",
        "size": 100663296,
        "url": "https://example.com/full/vendor_boot.img"
      }
    ],
    "os_patch_level": "2026-06-01",
    "os_sdk_level": 36,
    "ota_property_files": "payload_metadata.bin:4662:187245,payload.bin:4662:1926274191,payload_properties.txt:1926278911:156,apex_info.pb:2220:1279,care_map.pb:3546:1069,metadata:69:683,metadata.pb:820:1352                        ",
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
