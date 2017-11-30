#!/bin/sh

KEYSTORE="testkey.jks"
KEYSTORE_PASSWORD="android"
KEY_PASSWORD="android"
ALIAS="android"
PRIVATE_KEY="../../../build/target/product/security/testkey.pk8"
CERTIFICATE="../../../build/target/product/security/testkey.x509.pem"

tmpdir=`mktemp -d`
trap 'rm -r $tmpdir;' 0

key="$tmpdir/platform.key"
pk12="$tmpdir/platform.pk12"
openssl pkcs8 -in "$PRIVATE_KEY" -inform DER -outform PEM -nocrypt -out "$key"
openssl pkcs12 -export -in "$CERTIFICATE" -inkey "$key" -name "$ALIAS" -out "$pk12" -password pass:"$KEY_PASSWORD"

# Import key
keytool -importkeystore \
    -srckeystore "$pk12" -srcstoretype pkcs12 -srcstorepass "$KEY_PASSWORD" \
    -destkeystore "$KEYSTORE" -deststorepass "$KEYSTORE_PASSWORD" -destkeypass "$KEY_PASSWORD"
