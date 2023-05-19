#!/bin/bash
#-------------------------------------------------------------------------------
# Copyright 2023 Norconex Inc.
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

# Gets all directories with a pom.xml in them and returns them all
# separated by spaces

all_project_dirs=""
for pomFile in $(find . -name "pom.xml" -not -path "*/target/*"); do
    dir=$(echo $pomFile | sed -n 's|^\./\(.*\)/pom.xml$|\1|gp');
    all_project_dirs="${all_project_dirs} ${dir}";
done

echo $all_project_dirs