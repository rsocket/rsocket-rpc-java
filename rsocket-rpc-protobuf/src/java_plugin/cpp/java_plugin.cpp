#include <memory>

#include "java_generator.h"
#include "blocking_java_generator.h"
#include <google/protobuf/compiler/code_generator.h>
#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <iostream>

static string JavaPackageToDir(const string& package_name) {
  string package_dir = package_name;
  for (size_t i = 0; i < package_dir.size(); ++i) {
    if (package_dir[i] == '.') {
      package_dir[i] = '/';
    }
  }
  if (!package_dir.empty()) package_dir += "/";
  return package_dir;
}

class JavaRSocketRpcGenerator : public google::protobuf::compiler::CodeGenerator {
 public:
  JavaRSocketRpcGenerator() {}
  virtual ~JavaRSocketRpcGenerator() {}

  virtual bool Generate(const google::protobuf::FileDescriptor* file,
                        const string& parameter,
                        google::protobuf::compiler::GeneratorContext* context,
                        string* error) const {
    return reactor(file, parameter, context, error) && blocking(file, parameter, context, error);
   // return reactor(file, parameter, context, error);
  }

  virtual bool reactor(const google::protobuf::FileDescriptor* file,
                          const string& parameter,
                          google::protobuf::compiler::GeneratorContext* context,
                          string* error) const {
    std::vector<std::pair<string, string> > options;
    google::protobuf::compiler::ParseGeneratorParameter(parameter, &options);

    java_rsocket_rpc_generator::ProtoFlavor flavor =
     java_rsocket_rpc_generator::ProtoFlavor::NORMAL;

    bool disable_version = false;
    for (size_t i = 0; i < options.size(); i++) {
        if (options[i].first == "lite") {
            flavor = java_rsocket_rpc_generator::ProtoFlavor::LITE;
        } else if (options[i].first == "noversion") {
            disable_version = true;
        }
    }

    string package_name = java_rsocket_rpc_generator::ServiceJavaPackage(file);
    string package_filename = JavaPackageToDir(package_name);
    for (int i = 0; i < file->service_count(); ++i) {
        const google::protobuf::ServiceDescriptor* service = file->service(i);

        string interface_filename = package_filename + service->name() + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> interface_file(context->Open(interface_filename));
        java_rsocket_rpc_generator::GenerateInterface(service, interface_file.get(), flavor, disable_version);

        string client_filename = package_filename + java_rsocket_rpc_generator::ClientClassName(service) + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> client_file(context->Open(client_filename));
        java_rsocket_rpc_generator::GenerateClient(service, client_file.get(), flavor, disable_version);

        string server_filename = package_filename + java_rsocket_rpc_generator::ServerClassName(service) + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> server_file(context->Open(server_filename));
        java_rsocket_rpc_generator::GenerateServer(service, server_file.get(), flavor, disable_version);
    }
    return true;
  }

  virtual bool blocking(const google::protobuf::FileDescriptor* file,
                          const string& parameter,
                          google::protobuf::compiler::GeneratorContext* context,
                          string* error) const {
    std::vector<std::pair<string, string> > options;
    google::protobuf::compiler::ParseGeneratorParameter(parameter, &options);

    blocking_java_rsocket_rpc_generator::ProtoFlavor flavor =
    blocking_java_rsocket_rpc_generator::ProtoFlavor::NORMAL;

    bool disable_version = false;
    bool generate_blocking_api = false;

    for (size_t i = 0; i < options.size(); i++) {
        const string& option = options[i].first;
        if (option == "lite") {
            flavor = blocking_java_rsocket_rpc_generator::ProtoFlavor::LITE;
        } else if (option == "noversion") {
            disable_version = true;
        } else if (option == "generate-blocking-api") {
            generate_blocking_api = true;
        }
    }

    if (!generate_blocking_api) {
        return true;
    }

    string package_name = blocking_java_rsocket_rpc_generator::ServiceJavaPackage(file);
    string package_filename = JavaPackageToDir(package_name);
    for (int i = 0; i < file->service_count(); ++i) {
        const google::protobuf::ServiceDescriptor* service = file->service(i);

        string interface_filename = package_filename + "Blocking" + service->name() + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> interface_file(context->Open(interface_filename));
        blocking_java_rsocket_rpc_generator::GenerateInterface(service, interface_file.get(), flavor, disable_version);

        string client_filename = package_filename + "Blocking" + java_rsocket_rpc_generator::ClientClassName(service) + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> client_file(context->Open(client_filename));
        blocking_java_rsocket_rpc_generator::GenerateClient(service, client_file.get(), flavor, disable_version);

        string server_filename = package_filename + "Blocking" + java_rsocket_rpc_generator::ServerClassName(service) + ".java";
        std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> server_file(context->Open(server_filename));
        blocking_java_rsocket_rpc_generator::GenerateServer(service, server_file.get(), flavor, disable_version);
    }
    return true;
  }

};

int main(int argc, char* argv[]) {
  JavaRSocketRpcGenerator generator;
  return google::protobuf::compiler::PluginMain(argc, argv, &generator);
}
