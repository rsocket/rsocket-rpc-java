#!/bin/sh
set -e
# check to see if protobuf folder is empty
if [ ! -d "$HOME/google/protobuf/lib" ]; then
  wget https://github.com/google/protobuf/releases/download/v3.6.1/protobuf-cpp-3.6.1.tar.gz
  tar -xzvf protobuf-cpp-3.6.1.tar.gz
  pushd protobuf-3.6.1 && ./configure --disable-shared && make && sudo make install && popd
else
  echo "Using cached directory."
fi