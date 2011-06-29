mkdir -p classes
javac -d classes src/adaptorlib/*java src/adaptorlib/tests/*java src/adaptortemplate/*java src/filesystemadaptor/*java
copy src/logging.properties classes/
