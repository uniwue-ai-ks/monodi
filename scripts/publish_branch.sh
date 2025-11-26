#!/bin/sh
git switch public
git merge --squash ${1:-master}
git commit -m "squashed commit" --edit
git push
git switch ${1:-master}
git merge public
