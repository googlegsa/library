#!/bin/sh
java -Djava.util.logging.config.file=classes/logging.properties -cp classes adaptortemplate.AdaptorTemplate "$@"
