#!/bin/sh

BRANCH="$(git branch --show-current)"

echo "Main Branch: $BRANCH"

INTERNALS="micro-ui-internals"
cd ..

cp microplan/App.js src
cp microplan/package.json package.json 
cp microplan/webpack.config.js webpack.config.js 
cp microplan/inter-package.json $INTERNALS/package.json

cp $INTERNALS/example/src/UICustomizations.js src/Customisations

echo "UI :: microplan " && echo "Branch: $(git branch --show-current)" && echo "$(git log -1 --pretty=%B)" && echo "installing packages" 

