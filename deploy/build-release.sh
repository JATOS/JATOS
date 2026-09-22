#!/usr/bin/env bash

set -Eeuo pipefail

# ---------------------------------------------------------------------------
# JATOS release configuration
# ---------------------------------------------------------------------------

JAVA_MAJOR="25"

# Pin this for reproducible releases.
# Leave empty to download the latest Java release.
#JAVA_VERSION="25.0.4+7"
JAVA_VERSION=""

DOCKER_IMAGE="jatos/jatos"

LINUX_JRE_DIR="linux_x64_jre"
WINDOWS_JRE_DIR="win64_jre"
MAC_X64_JRE_DIR="mac_x64_jre"
MAC_ARM64_JRE_DIR="mac_aarch64_jre"

OUTPUT_DIR="target/release"
WORK_DIR="target/release-work"
JRE_CACHE="target/release-jres"

# ---------------------------------------------------------------------------

SBT_TASKS=(clean test dist)
CREATE_JAVA_BUNDLES=true
PUSH_DOCKER=false
DOCKER_TAG_LATEST=false
CREATE_GITHUB_RELEASE=false
RELEASE_NOTES_FILE="deploy/RELEASE_NOTES.md"

usage() {
    cat <<EOF
Usage:
  $0 [options]

Options:
  --skip-clean
      Skip the cleaning

  --skip-tests
      Skip the testing

  --skip-java-bundles
      Create only jatos.zip and skip all bundled-JRE packages.

  --push-docker
      Build and push the Docker images.

  --docker-tag-latest
      Also tag the Docker image as "latest".

  --github-release
      Create a draft GitHub release.

Example:
  $0

  $0 -h

  $0 \\
     --skip-tests \\
     --push-docker \\
     --github-release
EOF
}

die() {
    echo "Error: $*" >&2
    exit 1
}

log() {
    echo >&2
    echo "==> $*" >&2
}

require() {
    command -v "$1" >/dev/null ||
        die "Required command not found: $1"
}

remove_task() {
    local task="$1"
    local new_tasks=()

    for t in "${SBT_TASKS[@]}"; do
        [[ "$t" != "$task" ]] && new_tasks+=("$t")
    done

    SBT_TASKS=("${new_tasks[@]}")
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-clean)
                remove_task clean
                ;;

            --skip-tests)
                remove_task test
                ;;

            --skip-java-bundles)
                CREATE_JAVA_BUNDLES=false
                ;;

            --push-docker)
                PUSH_DOCKER=true
                ;;

            --docker-tag-latest)
                DOCKER_TAG_LATEST=true
                ;;

            --github-release)
                CREATE_GITHUB_RELEASE=true
                ;;

            -h|--help)
                usage
                exit 0
                ;;

            *)
                die "Unknown option: $1"
                ;;
        esac

        shift
    done
}

check_tools() {
    require curl
    require git
    require sbt
    require tar
    require unzip
    require zip
    require sha256sum

    if [[ "$PUSH_DOCKER" == true ]]; then
        require docker
    fi

    if [[ "$CREATE_GITHUB_RELEASE" == true ]]; then
        require gh
    fi
}

read_version() {
    [[ -f VERSION ]] ||
        die "Missing VERSION file."

    VERSION="$(tr -d '[:space:]' < VERSION)"

    [[ -n "$VERSION" ]] ||
        die "VERSION must not be empty."

    [[ "$VERSION" != v* ]] ||
        die "VERSION must not start with 'v'. Use '$VERSION' only for GitHub tags; VERSION should contain the raw app version."

    [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$ ]] ||
        die "Invalid version in VERSION: $VERSION"

    git diff --exit-code -- VERSION >/dev/null ||
        die "VERSION has uncommitted changes. Commit it before releasing."
}

adoptium_url() {
    local os="$1"
    local arch="$2"

    if [[ -n "$JAVA_VERSION" ]]; then
        local encoded_version="${JAVA_VERSION//+/%2B}"

        echo "https://api.adoptium.net/v3/binary/version/${encoded_version}/${os}/${arch}/jre/hotspot/normal/eclipse"
    else
        echo "https://api.adoptium.net/v3/binary/latest/${JAVA_MAJOR}/ga/${os}/${arch}/jre/hotspot/normal/eclipse"
    fi
}

download_jre() {
    local name="$1"
    local os="$2"
    local arch="$3"
    local extension="$4"

    local archive="$JRE_CACHE/${name}.${extension}"
    local temporary="${archive}.part"
    local url

    url="$(adoptium_url "$os" "$arch")"

    if [[ ! -s "$archive" ]]; then
        log "Downloading Temurin JRE for $name"

        rm -f -- "$temporary"

        if ! curl \
            --fail \
            --location \
            --retry 3 \
            --output "$temporary" \
            "$url"
        then
            rm -f -- "$temporary"
            die "Could not download Temurin JRE from: $url"
        fi

        mv -- "$temporary" "$archive"
    fi

    printf '%s\n' "$archive"
}

extract_jre() {
    local archive="$1"
    local destination="$2"

    mkdir -p "$destination"

    case "$archive" in
        *.zip)
            unzip -q "$archive" -d "$destination"
            ;;

        *.tar.gz)
            tar -xzf "$archive" -C "$destination"
            ;;

        *)
            die "Unsupported archive: $archive"
            ;;
    esac
}

find_jre_root() {
    local extraction_dir="$1"
    local java_path="$2"

    local java_executable

    java_executable="$(
        find "$extraction_dir" \
            -path "*/$java_path" \
            -type f \
            -print \
            -quit
    )"

    [[ -n "$java_executable" ]] ||
        die "Could not find $java_path in $extraction_dir"

    echo "${java_executable%/$java_path}"
}

prepare_bundle() {
    local name="$1"
    local bundle_dir="$WORK_DIR/$name"
    local bundle_root="$bundle_dir/jatos"

    [[ -n "${BASE_DIR:-}" && -d "$BASE_DIR" ]] ||
        die "BASE_DIR is missing."

    rm -rf -- "$bundle_dir"
    mkdir -p "$bundle_root"

    cp -a -- "$BASE_DIR/." "$bundle_root/"
    rm -rf -- "$bundle_root/jre"

    echo "$bundle_root"
}

add_jre() {
    local bundle_root="$1"
    local archive="$2"
    local jre_dir="$3"
    local java_path="$4"
    local name="$5"

    local extraction_dir="$WORK_DIR/extracted-$name"
    local source_root

    rm -rf -- "$extraction_dir"
    extract_jre "$archive" "$extraction_dir"

    source_root="$(find_jre_root "$extraction_dir" "$java_path")"

    mkdir -p "$bundle_root/jre/$jre_dir"
    cp -a -- "$source_root/." "$bundle_root/jre/$jre_dir/"
}

create_zip() {
    local bundle_root="$1"
    local filename="$2"

    mkdir -p "$OUTPUT_DIR"
    rm -f -- "$OUTPUT_DIR/$filename"

    (
        cd "$(dirname "$bundle_root")"
        zip -q -r -y "$OLDPWD/$OUTPUT_DIR/$filename" "$(basename "$bundle_root")"
    )

    unzip -tq "$OUTPUT_DIR/$filename" >/dev/null
}

create_plain_release() {
    log "Creating jatos.zip"

    local bundle_root
    bundle_root="$(prepare_bundle plain)"

    create_zip "$bundle_root" "jatos.zip"
}

create_java_release() {
    local name="$1"
    local os="$2"
    local arch="$3"
    local extension="$4"
    local jre_dir="$5"
    local java_path="$6"
    local filename="$7"

    log "Creating $filename"

    local archive
    local bundle_root

    archive="$(download_jre "$name" "$os" "$arch" "$extension")"
    bundle_root="$(prepare_bundle "$name")"

    add_jre \
        "$bundle_root" \
        "$archive" \
        "$jre_dir" \
        "$java_path" \
        "$name"

    create_zip "$bundle_root" "$filename"
}

build_jatos() {
    log "Building JATOS $VERSION"

    log "Running: sbt ${SBT_TASKS[*]}"

    sbt "${SBT_TASKS[@]}"

    rm -rf -- "$WORK_DIR"
    mkdir -p "$WORK_DIR"

    local distributions=(target/universal/*.zip)

    [[ ${#distributions[@]} -eq 1 ]] ||
        die "Expected exactly one ZIP in target/universal."

    local extracted="$WORK_DIR/base"

    mkdir -p "$extracted"
    unzip -q "${distributions[0]}" -d "$extracted"

    local roots=("$extracted"/*)

    [[ ${#roots[@]} -eq 1 && -d "${roots[0]}" ]] ||
        die "Unexpected SBT distribution structure."

    BASE_DIR="${roots[0]}"
}

generate_checksums() {
    log "Generating checksums"

    local files=(
        jatos.zip
    )

    if [[ "$CREATE_JAVA_BUNDLES" == true ]]; then
        files+=(
            "jatos_linux_java${JAVA_MAJOR}.zip"
            "jatos_mac_aarch64_java${JAVA_MAJOR}.zip"
            "jatos_mac_x64_java${JAVA_MAJOR}.zip"
            "jatos_win_java${JAVA_MAJOR}.zip"
        )
    fi

    (
        cd "$OUTPUT_DIR"
        sha256sum "${files[@]}" >SHA256SUMS
    )
}

publish_docker() {
    [[ "$PUSH_DOCKER" == true ]] || return 0

    log "Building and pushing Docker images"

    sbt "Assets / clean" "Docker / stage"

    local tags=(
        --tag "$DOCKER_IMAGE:$VERSION"
    )

    if [[ "$DOCKER_TAG_LATEST" == true ]]; then
        tags+=(
            --tag "$DOCKER_IMAGE:latest"
        )
    fi

# If buildx container doesn't exist:
# DOCKER_CONFIG="$HOME/.docker-jatos" docker login --username YOUR_DOCKERHUB_USERNAME
# docker rm -f buildx_buildkit_jatos-builder0
# DOCKER_CONFIG="$HOME/.docker-jatos" \
# docker buildx create \
#     --name jatos-builder \
#     --driver docker-container \
#     --use \
#     --bootstrap

    DOCKER_CONFIG="$HOME/.docker-jatos" \
    docker buildx build \
        --builder jatos-builder \
        --file deploy/Dockerfile \
        --platform linux/amd64,linux/arm64 \
        --progress plain \
        --push \
        "${tags[@]}" \
        target/docker/stage
}

create_github_release() {
    [[ "$CREATE_GITHUB_RELEASE" == true ]] || return 0

    log "Creating draft GitHub release"

    if gh release view "v$VERSION" >/dev/null 2>&1; then
        die "GitHub release v$VERSION already exists."
    fi

    local arguments=(
        release create "v$VERSION"
        --draft
        --title "JATOS v$VERSION"
    )

    [[ -f "$RELEASE_NOTES_FILE" ]] ||
        die "Release notes not found: $RELEASE_NOTES_FILE"

    arguments+=(--notes-file "$RELEASE_NOTES_FILE")

    arguments+=(
        "$OUTPUT_DIR/jatos.zip"
        "$OUTPUT_DIR/SHA256SUMS"
    )

    if [[ "$CREATE_JAVA_BUNDLES" == true ]]; then
        arguments+=(
            "$OUTPUT_DIR/jatos_linux_java${JAVA_MAJOR}.zip"
            "$OUTPUT_DIR/jatos_mac_aarch64_java${JAVA_MAJOR}.zip"
            "$OUTPUT_DIR/jatos_mac_x64_java${JAVA_MAJOR}.zip"
            "$OUTPUT_DIR/jatos_win_java${JAVA_MAJOR}.zip"
        )
    fi

    gh "${arguments[@]}"
}

main() {
    parse_arguments "$@"
    check_tools

    git rev-parse --show-toplevel >/dev/null 2>&1 ||
        die "Run this script inside the JATOS Git repository."

    cd "$(git rev-parse --show-toplevel)"

    read_version

    build_jatos

    rm -rf -- "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"

    create_plain_release

    if [[ "$CREATE_JAVA_BUNDLES" == true ]]; then
        mkdir -p "$JRE_CACHE"

        create_java_release \
            linux-x64 \
            linux \
            x64 \
            tar.gz \
            "$LINUX_JRE_DIR" \
            bin/java \
            "jatos_linux_java${JAVA_MAJOR}.zip"

        create_java_release \
            windows-x64 \
            windows \
            x64 \
            zip \
            "$WINDOWS_JRE_DIR" \
            bin/java.exe \
            "jatos_win_java${JAVA_MAJOR}.zip"

        create_java_release \
            mac-x64 \
            mac \
            x64 \
            tar.gz \
            "$MAC_X64_JRE_DIR" \
            Contents/Home/bin/java \
            "jatos_mac_x64_java${JAVA_MAJOR}.zip"

        create_java_release \
            mac-aarch64 \
            mac \
            aarch64 \
            tar.gz \
            "$MAC_ARM64_JRE_DIR" \
            Contents/Home/bin/java \
            "jatos_mac_aarch64_java${JAVA_MAJOR}.zip"
    else
        log "Skipping Java-bundled releases"
    fi

    generate_checksums
    publish_docker
    create_github_release

    rm -rf -- "$WORK_DIR"

    log "Release $VERSION completed"

    printf "\nContent of %s:\n" $OUTPUT_DIR
    ls -lh "$OUTPUT_DIR"
}

main "$@"
