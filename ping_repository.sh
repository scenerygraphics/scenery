#!/bin/bash
repo_param=$1
repo="${repo_param//\//%2F}"
api_token=$2

echo "Pinging repository $repo_param ($repo) for rebuild ..."

body='{
"request": {
    "branch":"master",
    "message":"Triggered CI build from scenerygraphics/scenery"
}}'

curl -s -X POST \
   -H "Content-Type: application/json" \
   -H "Accept: application/json" \
   -H "Travis-API-Version: 3" \
   -H "Authorization: token $api_token" \
   -d "$body" \
   https://api.travis-ci.org/repo/$repo/requests
