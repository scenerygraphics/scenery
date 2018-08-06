#!/bin/bash
LS_CMD="ls *.{vert,geom,tese,tesc,frag,comp} 2>/dev/null"
POST_CMD="grep --color -i ERROR"
OPTIMISE=false

while getopts ":vO" opt; do
    case $opt in
        v)
            echo "Verbose mode activated"
            LS_CMD="$LS_CMD 2>/dev/null"
            POST_CMD="cat"
            ;;
        O)
            echo "Optimisations activated, use with care."
            if hash spirv-opt 2>/dev/null; then
                OPTIMISE=true
                SPIRV_OPT=$(which spirv-opt)
            else
                echo "Warning: spirv-opt not found, optimisations disabled."
                OPTIMISE=false
            fi
            ;;
    esac
done


files=$(eval "$LS_CMD")
for i in $files; do
    echo -n "# $i GLSL -> SPIR-V"

    # compile to SPV with Vulkan semantics
    glslangValidator --aml -V $i -o $i.spv.tmp | $POST_CMD

    # check if ifdefs are needed in the file, if yes, skip
    grep '#ifdef\|#ifndef\|#else\|#endif' $i &>/dev/null
    if [ $? == 0 ]; then
   	echo " (skipping because of preprocessor statements)"
	rm -f $i.spv $i.spv.tmp
	continue
    fi

    # optimise, if spirv-opt was found
    if $OPTIMISE; then
        $SPIRV_OPT -O -o $i.spv $i.spv.tmp
        rm $i.spv.tmp
    # otherwise, just use the unoptimised SPV binary
    else
        mv $i.spv.tmp $i.spv
    fi

    echo -e "\e[92m ✓ \e[39m"
done;
