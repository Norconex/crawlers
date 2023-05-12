#!/bin/bash

# Returns the maven parameters required to run a mvn command only 
# on projects with changed files. If the Maven parent project was modified,
# an empty string is returned (i.e., a regular maven build on parent without 
# project filtering).
#
# Relies on  ./github/outputs to be present, previously generated
# by 'tj-actions/changed-files' action.


OUTPUTS_DIR=.github/outputs
CHANGES_FILE=all_changed_files.txt

parent_changed=$(cd $OUTPUTS_DIR; cat $CHANGES_FILE | grep -v -e ^committer -e ^importer -e ^crawler | wc -l)

if [ $parent_changed -gt 999999 ]; then
    # maven parent has changed, so don't filter to build all projects
    echo "";
    exit;
fi

# Filter on modified artifacts

#artifacts=$(cat $changed_dirs | grep -e ^committer -e ^importer -e ^crawler | sed 's_^\(committer\|crawler\)/\([^\s]*\)_:\1-\2_' | sed 's_^\(importer\)/.*$_:\1_' | sort | uniq)

#dirs=($(cat $changed_dirs))

#echo "DIRS: ${dirs[1]}"




#artifacts=$(echo $dirs | grep -e ^committer -e ^importer -e ^crawler | sed 's_^\(committer\|crawler\)/\([^/]*\)/.*$_:\1-\2_' | sed 's_^\(importer\)/.*$_:\1_' | sort | uniq)
#artifacts=$(cat $changed_dirs | grep -e ^committer -e ^importer -e ^crawler | sed 's_^\(committer\|crawler\)/\([^/]*\)/.*$_:\1-\2_' | sed 's_^\(importer\)/.*$_:\1_' | sort | uniq)
artifacts=$(cd $OUTPUTS_DIR; cat $CHANGES_FILE | tr ' ' '\n' | grep -e ^committer -e ^importer -e ^crawler | sed 's_^\(committer\|crawler\)/\(.*\)$_:\1-\2_' | sed 's_^\(importer\)/.*$_:\1_' | sort | uniq | tr '\n' ' ')

echo "ARTIFACTS: $artifacts"

#If this returns, do a regular maven build on parent:
#cat deleteme.txt | grep -v -e committer -e importer -e crawler | wc -l

#Else, maven build only affected ones

#cat deleteme.txt | grep -e ^committer -e ^importer -e ^crawler | sed 's_^\(committer\|crawler\)/\([^/]*\)/.*$_:\1-\2_' | sed 's_^\(importer\)/.*$_:\1_' | sort | uniq



#echo $(ls -l .github/outputs) 