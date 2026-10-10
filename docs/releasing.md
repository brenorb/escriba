# Release signing

The distribution APK is R8-optimized and signed with the owner's local release key. `.signing/` is ignored by Git and contains the keystore and its password, with restrictive permissions. Preserve it securely to sign compatible upgrades. Neither key nor password belongs in GitHub artifacts or logs.

```sh
./gradlew assembleRelease
scripts/sign-release.sh
```

The signing script creates a key on its first run only. It never overwrites an existing key. It writes `artifacts/escriba.apk` and checks the signature. CI builds unsigned release APKs and debug APKs; publishing official signed releases uses the local script or separately provisioned GitHub secrets.
