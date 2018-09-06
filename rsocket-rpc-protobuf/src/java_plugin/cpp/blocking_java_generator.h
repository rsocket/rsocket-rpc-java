#ifndef RSOCKET_RPC_COMPILER_BLOCKING_JAVA_GENERATOR_H_
#define RSOCKET_RPC_COMPILER_BLOCKING_JAVA_GENERATOR_H_

#include <stdlib.h>  // for abort()
#include <iostream>
#include <string>

#include <google/protobuf/io/zero_copy_stream.h>
#include <google/protobuf/descriptor.h>


// Abort the program after logging the mesage if the given condition is not
// true. Otherwise, do nothing.
#define RSOCKET_RPC_CODEGEN_CHECK(x) !(x) && LogHelper(&std::cerr).get_os() \
                             << "CHECK FAILED: " << __FILE__ << ":" \
                             << __LINE__ << ": "

// Abort the program after logging the mesage.
#define RSOCKET_RPC_CODEGEN_FAIL RSOCKET_RPC_CODEGEN_CHECK(false)

using namespace std;

namespace blocking_java_rsocket_rpc_generator {

enum ProtoFlavor {
  NORMAL, LITE
};

// Returns the package name of the RSocket RPC services defined in the given file.
string ServiceJavaPackage(const google::protobuf::FileDescriptor* file);

// Returns the name of the client class for the given service.
string ClientClassName(const google::protobuf::ServiceDescriptor* service);

// Returns the name of the client class for the given service.
string ServerClassName(const google::protobuf::ServiceDescriptor* service);

// Writes the generated interface into the given ZeroCopyOutputStream
void GenerateInterface(const google::protobuf::ServiceDescriptor* service,
                       google::protobuf::io::ZeroCopyOutputStream* out,
                       ProtoFlavor flavor,
                       bool disable_version);

// Writes the generated client into the given ZeroCopyOutputStream
void GenerateClient(const google::protobuf::ServiceDescriptor* service,
                    google::protobuf::io::ZeroCopyOutputStream* out,
                    ProtoFlavor flavor,
                    bool disable_version);

// Writes the generated server into the given ZeroCopyOutputStream
void GenerateServer(const google::protobuf::ServiceDescriptor* service,
                    google::protobuf::io::ZeroCopyOutputStream* out,
                    ProtoFlavor flavor,
                    bool disable_version);

}  // namespace java_rsocket_rpc_generator

#endif  // RSOCKET_RPC_COMPILER_BLOCKING_JAVA_GENERATOR_H_
