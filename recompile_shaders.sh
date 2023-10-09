#!/bin/sh
cd src/main/resources/graphics/scenery/backends/shaders
./generate-spirv.sh -O $@
