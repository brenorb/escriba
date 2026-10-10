#!/bin/sh
# A single command provisions the supported public CLI and starts the adapter.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tools_dir=${ESCRIBA_TOOLS_DIR:-${XDG_CACHE_HOME:-"$HOME/.cache"}/escriba/tools}
revision=cadda438a87ba8eeb52f1794cbb66017a9015478

if ! command -v uv >/dev/null 2>&1 || ! command -v cargo >/dev/null 2>&1; then
    if command -v brew >/dev/null 2>&1; then
        brew install uv rust
    else
        echo 'Install uv and Rust/Cargo once, then run this command again.' >&2
        echo 'https://docs.astral.sh/uv/ and https://rustup.rs/' >&2
        exit 1
    fi
fi
mkdir -p "$tools_dir"
if [ ! -x "$tools_dir/bin/fscript" ] || [ "$(cat "$tools_dir/escriba-revision" 2>/dev/null || true)" != "$revision" ]; then
    cargo install --locked --git https://github.com/brenorb/fast-transcript --rev "$revision" --root "$tools_dir" --force
    echo "$revision" > "$tools_dir/escriba-revision"
fi
exec python3 "$project_dir/server/escriba_server.py" --binary "$tools_dir/bin/fscript" "$@"
