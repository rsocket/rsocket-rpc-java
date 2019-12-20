#!/usr/bin/env bash

FILE=./protobuf-3.6.1
if [ ! -d "$FILE" ]; then
  echo 'install protobuf from scratch'
  curl -O -L https://github.com/google/protobuf/releases/download/v3.6.1/protobuf-cpp-3.6.1.tar.gz
  tar -xzf protobuf-cpp-3.6.1.tar.gz
  pushd protobuf-3.6.1 || exit
  ./autogen.sh
  ./configure --disable-shared && make && sudo make install
  popd || exit
else
  echo 'install protobuf from cache'
  pushd protobuf-3.6.1 || exit
  sudo make install
  popd || exit
fi