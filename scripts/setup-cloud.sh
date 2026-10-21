#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p /workspace/toolchains /workspace/setup /workspace/.gradle /workspace/.android
verify_download() {
  local download_url="$1" output="$2" expected="$3"
  if [[ ! -f "$output" ]] || ! printf '%s  %s\n' "$expected" "$output" | sha256sum --check --status; then
    curl --fail --location --retry 2 "$download_url" --output "$output"
  fi
  printf '%s  %s\n' "$expected" "$output" | sha256sum --check --status
}
if [[ ! -x /workspace/toolchains/java/bin/javac ]]; then
  verify_download https://download.oracle.com/java/21/archive/jdk-21.0.12.1_linux-x64_bin.tar.gz /workspace/setup/jdk.tar.gz 12f870b21301b42292558a3f872ce543affa2b86cb6458591c78388c41ddb111
  tar -xzf /workspace/setup/jdk.tar.gz -C /workspace/toolchains
  jdk_root="$(tar -tzf /workspace/setup/jdk.tar.gz | sed -n '1s@/.*@@p')"
  ln -s "$jdk_root" /workspace/toolchains/java
fi
if [[ ! -x /workspace/toolchains/node-v22.16.0-linux-x64/bin/node ]]; then
  verify_download https://nodejs.org/dist/v22.16.0/node-v22.16.0-linux-x64.tar.xz /workspace/setup/node.tar.xz f4cb75bb036f0d0eddf6b79d9596df1aaab9ddccd6a20bf489be5abe9467e84e
  tar -xJf /workspace/setup/node.tar.xz -C /workspace/toolchains
fi
if [[ ! -x /workspace/toolchains/android-sdk/cmdline-tools/latest/bin/sdkmanager ]]; then
  verify_download https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip /workspace/setup/android-tools.zip 0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
  mkdir -p /workspace/toolchains/android-sdk/cmdline-tools
  unzip -q /workspace/setup/android-tools.zip -d /workspace/toolchains/android-sdk/cmdline-tools
  mv /workspace/toolchains/android-sdk/cmdline-tools/cmdline-tools /workspace/toolchains/android-sdk/cmdline-tools/latest
fi
source "$repo_dir/scripts/cloud-env.sh"
# Reuse the platform's proxy and system CA trust, preserving TLS verification.
python3 - <<'PYSETUP'
import os, urllib.parse
from pathlib import Path
p=Path('/workspace/.gradle/gradle.properties')
existing=p.read_text() if p.exists() else ''
add={}
u=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if u.hostname:
    for protocol in ['http','https']:
        add[f'systemProp.{protocol}.proxyHost']=u.hostname
        add[f'systemProp.{protocol}.proxyPort']=str(u.port or 8080)
    add['systemProp.http.nonProxyHosts']='localhost|127.*|[::1]'
if Path('/etc/ssl/certs/java/cacerts').exists():
    add['systemProp.javax.net.ssl.trustStore']='/etc/ssl/certs/java/cacerts'
for k,v in add.items():
    if not any(line.startswith(k+'=') for line in existing.splitlines()): existing+=f'\n{k}={v}\n'
p.write_text(existing)
PYSETUP
sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
# SDK path is ignored by Git and never replaces an existing user configuration.
if [[ ! -f "$repo_dir/local.properties" ]]; then printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$repo_dir/local.properties"; fi
cd "$repo_dir"
npm ci
npm --prefix functions ci
./gradlew --no-daemon -PuseEmulators=true assembleDebug testDebugUnitTest lintDebug
node --test functions/test/policy.test.js
# Integration requires storage.googleapis.com in environment network settings.
if [[ "${RUN_FIREBASE_INTEGRATION:-0}" == 1 ]]; then npm test; fi
