#!/bin/bash

# Returns the maven parameters required to run a mvn command only 
# on projects with changed files. If the Maven parent project was modified,
# an empty string is returned (i.e., a regular maven build on parent without 
# project filtering).
#
# Relies on  ./github/outputs to be present, previously generated
# by 'tj-actions/changed-files' action.

echo $(ls -l .github/outputs) 