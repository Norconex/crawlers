#!/bin/bash

# Check if two arguments were provided
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <input1> <input2>"
  exit 1
fi

# Get the two input variables from the command-line arguments
version="$1"
type="$2"

# Append "-crawler-base" to the first input
result="FROM $version-crawler-base"

# Specify the name of the output file
output_file="Dockerfile-output"

# Save the result to the output file
echo "$result" > "$output_file"
echo >> $output_file
echo "WORKDIR /nxer" >> "$output_file"
echo >> $output_file
echo "RUN ls -l"  >> "$output_file"
echo >> $output_file

if [ "$type" = "crawler-es" ]; then
  echo "es"
  echo "RUN mkdir es ." >> "$output_file"
  echo >> $output_file

elif [ "$type" = "crawler-solr" ]; then
  echo "solr"
  echo "RUN mkdir solr ." >> "$output_file"
  echo >> $output_file

fi

echo "RUN ls -l" >> "$output_file"
echo >> $output_file
