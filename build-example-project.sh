#!/bin/bash
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

set -e
originalVersion=`cat gradle.properties | grep version | cut -d "=" -f2`
gitHash=`git rev-parse HEAD`
version="$originalVersion-${gitHash:0:7}"

echo -e "${RED}Original scenery version is $originalVersion, modified version for test build is $version${NC}"

echo -e "*** ${GREEN}Installing $version to local Maven repository...${NC}"
./gradlew publishToMavenLocal -Pversion=$version

echo -e "*** ${GREEN}Cloning test project ...${NC}"
rm -rf build/testProject
mkdir -p build/testProject
cd build/testProject
git clone https://github.com/scenerygraphics/minimal-scenery-example-project .

echo -e "*** ${GREEN}Building test project against scenery $version ...${NC}"
./gradlew --no-build-cache build -PsceneryVersion=$version -PmavenLocal=true
