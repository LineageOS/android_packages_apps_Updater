#!/bin/sh

if [ $# -ne 6 ]; then
    echo "Usage: `basename $0` PRIVATE_KEY CERTIFICATE \\"
    echo "          KEYSTORE_PASSWRD KEY_PASSWORD KEY_ALIAS \\"
    echo "          OUTPUT_KEYSTORE_PATH"
    echo
    echo "Example:"
    echo "  `basename $0` \\"
    echo "          ../../../build/target/product/security/testkey.pk8 \\"
    echo "          ../../../build/target/product/security/testkey.x509.pem \\"
    echo "          keystore-password key-password android testkey.jks"
    exit 0
fi

PRIVATE_KEY="$1"
CERTIFICATE="$2"
KEYSTORE_PASSWORD="$3"
KEY_PASSWORD="$4"
KEY_ALIAS="$5"
KEYSTORE_PATH="$6"

if [ -f "$KEYSTORE_PATH" ]; then
    echo "$KEYSTORE_PATH already exists"
    exit 1
fi

tmpdir=`mktemp -d`
trap 'rm -rf $tmpdir;' 0

key="$tmpdir/platform.key"
pk12="$tmpdir/platform.pk12"
openssl pkcs8 -in "$PRIVATE_KEY" -inform DER -outform PEM -nocrypt -out "$key"
if [ $? -ne 0 ]; then
    exit 1
fi
openssl pkcs12 -export -in "$CERTIFICATE" -inkey "$key" -name "$KEY_ALIAS" \
    -out "$pk12" -password pass:"$KEY_PASSWORD"
if [ $? -ne 0 ]; then
    exit 1
fi

keytool -importkeystore \
    -srckeystore "$pk12" -srcstoretype pkcs12 -srcstorepass "$KEY_PASSWORD" \
    -destkeystore "$KEYSTORE_PATH" -deststorepass "$KEYSTORE_PASSWORD" \
    -destkeypass "$KEY_PASSWORD"
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
