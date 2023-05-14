#!/bin/bash
#-------------------------------------------------------------------------------
# Copyright 2013-2017 Norconex Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#-------------------------------------------------------------------------------

# Returns a Maven deployment command. The project artifacts deployed
# will be filtered with arguments present in 
# ./github/outputs/mvn_projects-arg.txt (if present)
#

#####MVN_PROJECTS_ARG_FILE=.github/outputs/mvn_projects-arg.txt
#####
###### If Maven parent changed, build all, else, filter on changed projects
#####mvn_projects=""
#####
#####if [ -f "$MVN_PROJECTS_ARG_FILE" ]; then
#####    mvn_projects=$(cat "${MVN_PROJECTS_ARG_FILE}")
#####fi
#####
#####echo "mvn deploy:deploy $mvn_projects --threads=2";
echo "mvn deploy:deploy -DallowIncompleteProjects=true --threads=2";
