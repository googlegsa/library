#!/bin/sh
# Example gives the bytes of a document referenced with id.
#
# Shell equivalent of 
# com.google.enterprise.adaptor.examples.AdaptorTemplate.getDocContent

echo -e "GSA Adaptor Data Version 1 [\n]"
if test "5555" = "$1"; then
  echo "id=$1"
  echo content
  echo "This is the body of document 5555 from shell script"
elif test "9999" = "$1"; then
  echo "id=$1"
  echo content
  echo "This is the body of document 9999 from shell script"
else
  echo "id=$1"
  echo not-found
fi
