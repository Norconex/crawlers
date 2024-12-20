#!/bin/bash
#-------------------------------------------------------------------------------
# Copyright 2024 Norconex Inc.
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
set -e

# The Maven modules are in one or two level deep directories.
# We extract those from changed files and we compare them
# with entries in the parent pom.xml. Matches tell us which 
# module has some changes (compared to main).

# Extract modules from parent pom.xml (except shaded which is built separately)
modules=$(grep -oP '<module>\K.*?(?=</module>)' pom.xml | grep -v 'shaded')

# Get changed files from git diff
changed_dirs=$(git diff origin/main...HEAD --name-only)

# Eliminate the last segment of each path
top_level_and_files=$(echo "$changed_dirs" | xargs -n1 dirname | sort -u)

# Eliminate directories with more than 2 slashes
filtered_dirs=$(echo "$top_level_and_files" | grep -v '.*/.*/.*')

# Get one-level directories
one_level_dirs=$(echo "$filtered_dirs" | cut -d'/' -f1 | sort -u)

# Combine output of two-level and one-level directories
combined_dirs=$(echo -e "$filtered_dirs\n$one_level_dirs" | sort -u)

# Identify changed modules
changed_modules=()
for module in $modules; do
  for changed_dir in $combined_dirs; do
    if [[ "$changed_dir" == "$module" ]]; then
      changed_modules+=("$module")
    fi
  done
done

# Join the modules into a comma-separated string
changed_modules_string=$(IFS=,; echo "${changed_modules[*]}")

# Output the modules and changed modules for subsequent steps
echo "Changed modules: $changed_modules_string"
echo "changed_modules=$changed_modules_string" >> $GITHUB_ENV
