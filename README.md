# RSocket RPC - Java
[![Build Status](https://travis-ci.org/rsocket/rsocket-rpc-java.svg?branch=master)](https://travis-ci.org/rsocket/rsocket-rpc-java)

The standard [RSocket](http://rsocket.io) RPC Java implementation.

## Build from Source

1. Building rsocket-rpc-java requires installation of the [Protobuf](https://github.com/google/protobuf) compiler. RSocket RPC requires Protobuf 3.6.x or higher.

    For Mac users you can easily install the Protobuf compiler using Homebrew:

        $ brew install protobuf

    For other operating systems you can install the Protobuf compiler using the pre-built packages hosted on the [Protobuf Releases](https://github.com/google/protobuf/releases) page.

2. Run the following Gradle command to build the project:

        $ ./gradlew clean build
        
## What Next?

 * [Motivation](./docs/motivation.md)
 * [Get Started](./docs/get-started.md)  

## Release Notes

Please find release notes at [https://github.com/rsocket/rsocket-rpc-java/releases](https://github.com/rsocket/rsocket-rpc-java/releases).

## Bugs and Feedback

For bugs, questions, and discussions please use the [Github Issues](https://github.com/netifi/rsocket-rpc-java/issues).

## License
Copyright 2019 [Netifi Inc.](https://www.netifi.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
