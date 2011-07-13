#!/bin/sh

if test "1001" = "$1"; then
  echo "This is the body of document 1001 from shell script"
elif test "1002" = "$1"; then
  echo "This is the body of document 1002 from shell script"
else
  echo no document with id $1 found >&2
  exit 2
fi
