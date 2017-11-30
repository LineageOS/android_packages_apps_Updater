#!/bin/sh

if [ $# -lt 6 ]; then
    echo "Usage: `basename $0` KEYSTORE_PATH KEYSTORE_PASSWRD KEY_PASSWORD KEY_ALIAS \\"
    echo "          PRIVATE_KEY CERTIFICATE"
    echo
    echo "Example:"
    echo "  `basename $0` testkey.jks keystore-password key-password android \\"
    echo "          ../../../build/target/product/security/testkey.pk8 \\"
    echo "          ../../../build/target/product/security/testkey.x509.pem"
    exit 0
fi

KEYSTORE_PATH="$1"
KEYSTORE_PASSWORD="$2"
KEY_PASSWORD="$3"
KEY_ALIAS="$4"

PRIVATE_KEY="$5"
CERTIFICATE="$6"

if [ -f "$KEYSTORE_PATH" ]; then
    echo "$KEYSTORE_PATH exists already"
    exit 1
fi

tmpdir=`mktemp -d`
trap 'rm -r $tmpdir;' 0

key="$tmpdir/platform.key"
pk12="$tmpdir/platform.pk12"
openssl pkcs8 -in "$PRIVATE_KEY" -inform DER -outform PEM -nocrypt -out "$key"
if [ $? -ne 0 ]; then
    exit 1
fi
openssl pkcs12 -export -in "$CERTIFICATE" -inkey "$key" -name "$KEY_ALIAS" -out "$pk12" -password pass:"$KEY_PASSWORD"
if [ $? -ne 0 ]; then
    exit 1
fi

# Import key
keytool -importkeystore \
    -srckeystore "$pk12" -srcstoretype pkcs12 -srcstorepass "$KEY_PASSWORD" \
    -destkeystore "$KEYSTORE_PATH" -deststorepass "$KEYSTORE_PASSWORD" -destkeypass "$KEY_PASSWORD"
if [ $? -ne 0 ]; then
    exit 1
fi


echo
echo "Generating keystore.properties..."
if [ -f keystore.properties ]; then
    echo "keystore.properties already exists, overwrite it? [Y/n]"
    read reply
    if [ "$reply" = "n" -o "$reply" = "N" ]; then
        exit 0
    fi
fi

cat > keystore.properties <<EOF
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
storeFile=$KEYSTORE_PATH
storePassword=$KEYSTORE_PASSWORD
EOF
