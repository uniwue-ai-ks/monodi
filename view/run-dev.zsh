#!/bin/zsh
BASE=${0:a:h}

zparseopts -D -E a=all b=backend f=frontend s=staticfiles r=restart -restart=restart t:=mainttl -ttl:=mainttl S:=staticttl -staticttl:=staticttl h=help -help=help
if [[ $help ]]; then
	<<-EOF
	Usage: $0 [-a] [-b] [-f] [-s] [-r] [-t MAIN_TTL_FILE] [-S STATIC_TTL_FILE]
	 -a: all services (same as -b -f -s)

	 -b: backend (Fuseki)
	 -f: frontend (React)
	 -s: staticfiles (Caddy)

	 -r: restart selectedservices
	EOF
	exit
fi
if [[ -z $backend && -z $frontend && -z $staticfiles && -z $all && -z $restart ]]; then
	all='-a'
fi

if [[ $mainttl && -d ${mainttl[2]} ]]; then
	mainttl=( ${mainttl[2]:a}/* )
	mainttl="${mainttl[*]}"
	staticttl=""
else
	if [[ $mainttl && ! $staticttl && -e "${BASE}/rdf/${mainttl[2]:r}-static.ttl" ]]; then
		staticttl=${mainttl[2]:r}-static.ttl
	else
		staticttl=${staticttl[2]:-static.ttl}
	fi
	mainttl=${mainttl[2]:-datai.ttl}
fi

echo "Main TTL(s):   $mainttl\nStatic TTL: ${staticttl}\nExtra args: $*"

if command -v kitty &>/dev/null && [[ $KITTY_WINDOW_ID ]]; then
	kitty @goto-layout grid
	check-window() {
		kitty @ls | jq -e '[.[]|.tabs[]|.windows[]|.title]| any(. == "'$1'")' &>/dev/null
	}
	new-window() {
		zparseopts -D -E d:=dir -cwd:=dir
		title=$1; shift
		echo "kitty @launch --match window_id:$KITTY_WINDOW_ID ${dir:+--cwd} ${dir[2]} --keep-focus --title $title $* &>/dev/null"
		kitty @launch --hold --match window_id:$KITTY_WINDOW_ID ${dir:+--cwd} ${dir[2]} --keep-focus --title $title "$@" &>/dev/null
	}
	restart-window() {
		zparseopts -D -E d:=dir -cwd:=dir
		title=$1; shift
		kill $(kitty @ls | jq  '.[]|.tabs[]|.windows[] | select(.title == "'$title'") | .pid')
		echo "kitty @launch --match window_id:$KITTY_WINDOW_ID ${dir:+--cwd} ${dir[2]} --keep-focus --title $title $* &>/dev/null"
		kitty @launch --hold --match window_id:$KITTY_WINDOW_ID ${dir:+--cwd} ${dir[2]} --keep-focus --title $title "$@" &>/dev/null
	}
elif command -v tmux &>/dev/null && [[ $TMUX ]]; then
	echo "\e[33mWarning: no restart support or detection of already running instances in tmux\e[0m"
	tmux select-layout tiled
	check-window() { true }
	new-window() {
		zparseopts -D -E d:=dir -cwd:=dir
		title=$1; shift
		tmux split-window ${dir:+-c} ${dir[2]} -d "$@"
	}
	restart-window() { echo "Restarting not supported in tmux"; exit 1 }
fi

launch() {
	zparseopts -D -E r=restart -restart=restart d:=dir -cwd:=dir t:=title -title:=title
	if ! check-window ${title[2]} >/dev/null; then
		new-window ${dir[@]} ${title[2]} "$@"
	elif [[ $restart ]]; then
		restart-window ${dir[@]} ${title[2]} "$@"
	else
		echo "Running ${title[2]} detected"
	fi
}

fuseki() { launch $@ -d $BASE/rdf -t monodi:Fuseki sh -c "./01_create_tdb2_from_rdf.sh ${mainttl:a} ${staticttl:a} && ./02_start_fuseki.sh" }
react() { launch $@ -d $BASE/ui -t monodi:React sh -c 'REACT_APP_PDF_URL="http://localhost:8080/pdf/" npm start ' }
staticfiles() { launch $@ -d $BASE/docker/static -t monodi:HTTP caddy file-server --browse --listen 'localhost:8080' }

if [[ $all ]]; then
	fuseki $restart $@
	react $restart $@
	staticfiles $restart $@
else
	[[ $backend ]] && fuseki $restart $@
	[[ $frontend ]] && react $restart $@
	[[ $staticfiles ]] && staticfiles $restart $@
fi
