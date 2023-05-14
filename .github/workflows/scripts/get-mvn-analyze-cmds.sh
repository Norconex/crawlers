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

# Returns Maven Sonar analysis commands for modified projects, including 
# their dependents. Projects to be analysed are detected relying on 
# the presence of generated "/target/site/jacoco/jacoco.xml" files.
# We do not care whether the parent changed or not, since all modified ones 
# will have the Jacoco file (i.e., if the were all rebuilt due to 
# parent having been modified, they'll all be analysed).

# Relies on "mvn-build.sh" having run or other Maven command that
# would have generated the Jacoco in standard locations. 

echo "$(find . -name 'jacoco.xml' \
    | sed -n 's|^\(.*\)/target/.*$|cd \1 \&\& mvn sonar:sonar|gp')";

