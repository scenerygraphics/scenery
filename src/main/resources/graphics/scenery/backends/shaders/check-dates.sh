#!/bin/bash
LS_CMD="ls *.spv 2>/dev/null"
retval=0

echo "Comparing dates of compiled shader files vs. uncompiled..."
files=$(eval "$LS_CMD")
for i in $files; do
    spvdate=`git log -1 --format="%at" $i`
    textfile="${i%.*}"
    textdate=`git log -1 --format="%at" $textfile`

    if test $textdate -ge $spvdate
    then
        echo "ERROR: Last commit to $i is older than $textfile"
        retval=$((retval+1))
    fi
done;

echo "Found $retval SPV shaders that need to be updated and committed."

exit $retval
