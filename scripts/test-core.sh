#!/bin/sh
set -eu
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/dev/brenorb/escriba/InputPolicy.java app/src/main/java/dev/brenorb/escriba/EndpointPolicy.java app/src/main/java/dev/brenorb/escriba/Transcript.java app/src/main/java/dev/brenorb/escriba/Pcm.java app/src/main/java/dev/brenorb/escriba/VoiceModelPolicy.java app/src/main/java/dev/brenorb/escriba/ModelCatalog.java app/src/test/java/dev/brenorb/escriba/CoreSpec.java
java -cp "$out" dev.brenorb.escriba.CoreSpec
