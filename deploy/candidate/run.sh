stage=validate
trap 'printf "CANDIDATE_FAILED:%s\n" "$stage" >&3' ERR

digest=${SSM_ImageDigest:-}
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export AWS_PAGER="" AWS_MAX_ATTEMPTS=2
unset DOCKER_HOST DOCKER_CONTEXT COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_ENV_FILES
umask 077
state=/var/lib/shockgraph-ecr-candidate
region=us-east-2

stage=lock
[[ ! -L "$state" ]]
install -d -m 0700 -o root -g root "$state"
[[ ! -L "$state/.lock" ]]
exec 9>"$state/.lock"
flock -n 9
work=$(mktemp -d "$state/work.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
install -d -m 0700 "$work/docker"
export DOCKER_CONFIG="$work/docker"

stage=registry
account=$(aws sts get-caller-identity --region "$region" --query Account --output text)
[[ "$account" =~ ^[0-9]{12}$ ]]
registry="$account.dkr.ecr.$region.amazonaws.com"
export CANDIDATE_IMAGE="$registry/shockgraph-java-candidate@$digest"
aws ecr get-login-password --region "$region" |
    docker login --username AWS --password-stdin "$registry"
docker pull "$CANDIDATE_IMAGE"
docker image inspect "$CANDIDATE_IMAGE" |
    python3 -c 'import json,sys; sys.exit(sys.argv[1] not in json.load(sys.stdin)[0]["RepoDigests"])' "$CANDIDATE_IMAGE"

stage=settings
cat > "$work/compose.yaml" <<'SHOCKGRAPH_COMPOSE'
__COMPOSE_YAML__
SHOCKGRAPH_COMPOSE
env_file="$state/candidate.env"
[[ ! -L "$env_file" ]]
if [[ ! -e "$env_file" ]]; then
    {
        printf 'DB_PASSWORD=%s\n' "$(openssl rand -hex 32)"
        printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 32)"
        printf 'JWT_SECRET=%s\n' "$(openssl rand -hex 32)"
    } > "$work/candidate.env"
    mv -T -- "$work/candidate.env" "$env_file"
fi
[[ -f "$env_file" && $(stat -c '%u:%a' "$env_file") == 0:600 ]]
unset DB_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET
candidate_compose() {
    docker compose --project-name shockgraph-ecr-candidate --env-file "$env_file" \
        -f "$work/compose.yaml" "$@"
}
candidate_compose config --quiet

stage=start
candidate_compose up -d --no-build --wait --wait-timeout 240 mysql redis api

stage=smoke
check_candidate

printf 'CANDIDATE_VERIFIED_V1\n' >&3
