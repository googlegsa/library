#!/bin/sh
# Example gives bytes of file with particular id.

dir="/tmp/foo-bar/"
id="$1"
fn="$dir/$id"

echo -e "GSA Adaptor Data Version 1 [\n]"
echo "id=$id"

if test -f "$fn" && test -r "$fn"; then
  echo "content"
  cat "$fn"
else
  echo not-found
fi
