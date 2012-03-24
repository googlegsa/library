#!/bin/sh
# Example gives the bytes of a document referenced with id.
#
# Shell equivalent of 
# com.google.enterprise.adaptor.examples.AdaptorTemplate.getDocContent
#
# TODO: Update to contemporary format.

if test "1001" = "$1"; then
  echo "This is the body of document 1001 from shell script"
elif test "1002" = "$1"; then
  echo "This is the body of document 1002 from shell script"
else
  echo no document with id $1 found >&2
  exit 2
fi
