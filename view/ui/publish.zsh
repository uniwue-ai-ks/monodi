#!/bin/zsh
zparseopts -D -E l=latest -latest=latest d=date -date=date

autoload -z colors && colors

date_tag=$(date +%Y%m%d)

target_tags=()
if [[ -n $@ ]]; then
	if [[ -n $date ]]; then
		target_tags+=( monodi:${^@}-$date_tag )
	else
		target_tags+=( monodi:${^@} )
	fi
fi
if [[ -z $1 || $latest ]]; then
	target_tags+=( monodi:latest)
fi
if [[ -z $1 && $date ]]; then
	target_tags+=( monodi:$date_tag )
fi

echo "${fg[green]}Building monodi docker image with tags: ${target_tags[@]}${reset_color}"

tag_args=()
for tag in "${target_tags[@]}"; do
	tag_args+=( --tag "$tag" )
done

podman build . ${tag_args[@]} || exit 1

# to log in to harbor, create a login.sh script that runs podman login with the
# respective args, see login.zsh.example
if [[ -x ./login.sh ]]; then
	./login.sh
else
	echo "${fg[yellow]}No login.sh script found, relying on being logged in already.${reset_color}"
	echo "${fg[yellow]}Use \"podman login\" before running this or see login.sh.example for enabling auto-login.${reset_color}"
fi

for tag in "${target_tags[@]}"; do
	echo "${fg[green]}pushing tag: ${tag}${reset_color}"
	podman push ${tag} harbor-ls6.informatik.uni-wuerzburg.de/monodi-diw/$tag
done
