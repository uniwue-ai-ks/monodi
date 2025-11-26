#!/bin/sh
if ! [ -e .env ]; then
	echo -n "No .env file found, enter host name for the app: "
	read hostname
	echo "MONODI_BASE_URL=$hostname" > .env
fi

if ! command -v docker-compose >/dev/null 2>&1; then
	echo "docker-compose not found"
	exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
	if command -v podman >/dev/null 2>&1; then
		echo "docker not found, falling back to podman"
		docker() {
			podman "$@"
		}
		PODMAN=1
	else
		echo "Neither docker nor podman found"
		exit 1
	fi
fi


if [ -S ${XDG_RUNTIME_DIR}/podman/podman.sock ]; then
	export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
elif [ -n $PODMAN ]; then
	echo "Could not find podman socket, required for docker-compose"
	exit 1
fi

mkdir -p static/svg static/pdf

if ! docker volume inspect caddy_data >/dev/null 2>&1; then
	docker volume create caddy_data
fi
docker-compose "$@"
