mkdir -p classes
javac -d classes src/*/*java src/*/tests/*java
copy src/logging.properties classes/
