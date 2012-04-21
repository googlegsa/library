#!/bin/sh
# Example gives list of document ids in a directory.

dir="/tmp/foo-bar/"
cd "$dir"

echo -e "GSA Adaptor Data Version 1 [\n]"
echo id-list
# Note: \n delimiter requires no file names contain it.
find . -type f
