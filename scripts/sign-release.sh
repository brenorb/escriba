#!/bin/sh
set -eu
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK}"
mkdir -p .signing artifacts
chmod 700 .signing
if [ ! -f .signing/password ]; then
    python3 -c 'import secrets,pathlib,os;p=pathlib.Path(".signing/password");p.write_text(secrets.token_urlsafe(32));os.chmod(p,0o600)'
fi
if [ ! -f .signing/release.jks ]; then
    keytool -genkeypair -keystore .signing/release.jks -storepass:file .signing/password -keypass:file .signing/password -alias escriba -dname 'CN=Escriba' -keyalg RSA -keysize 3072 -validity 10000
    chmod 600 .signing/release.jks
fi
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk artifacts/escriba-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks .signing/release.jks --ks-pass file:.signing/password --ks-key-alias escriba --out artifacts/escriba.apk artifacts/escriba-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose artifacts/escriba.apk
(cd artifacts && shasum -a 256 escriba.apk > SHA256SUMS)
