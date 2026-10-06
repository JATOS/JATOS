#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
existing_image=""
if [[ ${1:-} == --image && $# == 2 ]]; then
    existing_image=$2
elif [[ $# != 0 ]]; then
    echo 'Usage: test/multinode/run.sh [--image PREBUILT_CURRENT_BRANCH_IMAGE]' >&2
    exit 2
fi
for command in docker node python3; do command -v "$command" >/dev/null; done
docker info >/dev/null
docker compose version >/dev/null
: "${JATOS_PLAYWRIGHT_MODULE:?Set this to an installed @playwright/test package with Chromium}"
node -e 'const p=require(process.env.JATOS_PLAYWRIGHT_MODULE); p.chromium.launch().then(b=>b.close()).catch(e=>{console.error(e);process.exit(1)})'

project="jatos-it-$(date -u +%Y%m%d%H%M%S)-$$"
output="$ROOT/.cache-tests/multi-node/$project"
mkdir -p "$output"
echo "Multi-node test project: $project"
echo "Diagnostics: $output"
export JATOS_TEST_IMAGE=${existing_image:-$project:local}
# Allocate once: Docker's automatic host ports can change when a container restarts.
read -r JATOS_IT_SEED_PORT JATOS_IT_WORKER_PORT JATOS_IT_PROXY_PORT < <(node <<'JS'
const net = require('node:net');
(async () => {
    const servers = await Promise.all([0,1,2].map(() => new Promise((resolve, reject) => {
        const server = net.createServer();
        server.on('error', reject);
        server.listen(0, '127.0.0.1', () => resolve(server));
    })));
    console.log(servers.map(server => server.address().port).join(' '));
    servers.forEach(server => server.close());
})().catch(error => { console.error(error); process.exit(1); });
JS
)
export JATOS_IT_SEED_PORT JATOS_IT_WORKER_PORT JATOS_IT_PROXY_PORT
printf 'JATOS_TEST_IMAGE=%s\nJATOS_IT_SEED_PORT=%s\nJATOS_IT_WORKER_PORT=%s\nJATOS_IT_PROXY_PORT=%s\n' \
    "$JATOS_TEST_IMAGE" "$JATOS_IT_SEED_PORT" "$JATOS_IT_WORKER_PORT" "$JATOS_IT_PROXY_PORT" > "$output/compose.env"
compose=(docker compose -p "$project" -f "$ROOT/test/multinode/compose.yaml")
cleanup() {
    status=$?
    trap - EXIT INT TERM
    set +e
    "${compose[@]}" logs --no-color > "$output/containers.log" 2>&1
    "${compose[@]}" ps -a --format json > "$output/containers.json" 2>&1
    "${compose[@]}" down --volumes --remove-orphans --timeout 20 > "$output/cleanup.log" 2>&1
    cleanup_status=$?
    [[ $cleanup_status == 0 ]] || status=1
    if [[ -z "$existing_image" ]]; then docker image rm "$JATOS_TEST_IMAGE" >> "$output/cleanup.log" 2>&1 || status=1; fi
    # Detect leaked project resources without touching other projects.
    for kind in container network volume; do
        if [[ $kind == container ]]; then
            remaining=$(docker container ls -aq --filter "label=com.docker.compose.project=$project") || status=1
        else
            remaining=$(docker "$kind" ls -q --filter "label=com.docker.compose.project=$project") || status=1
        fi
        if [[ -n "$remaining" ]]; then echo "Leaked $kind: $remaining" >> "$output/cleanup.log"; status=1; fi
    done
    echo "Diagnostics: $output"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
if [[ -z "$existing_image" ]]; then
    command -v sbt >/dev/null
    sbt 'Assets / clean' 'Docker / stage' > "$output/build.log" 2>&1
    docker build -f deploy/Dockerfile -t "$JATOS_TEST_IMAGE" target/docker/stage >> "$output/build.log" 2>&1
else
    docker image inspect "$JATOS_TEST_IMAGE" > /dev/null
fi
"${compose[@]}" up -d --wait --wait-timeout 240 > "$output/startup.log" 2>&1
export JATOS_IT_PROJECT="$project" JATOS_IT_OUTPUT="$output"
node test/multinode/scenarios.cjs 2>&1 | tee "$output/tests.log"
