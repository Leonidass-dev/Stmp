# frozen_string_literal: true
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: Configuration.proto

require 'google/protobuf'


descriptor_data = "\n\x13\x43onfiguration.proto\x12\x0f\x63om.hasup.proto\"m\n\rConfiguration\x12\x11\n\tserver_id\x18\x01 \x01(\x05\x12\x0c\n\x04host\x18\x02 \x01(\t\x12\x0c\n\x04port\x18\x03 \x01(\x05\x12\x17\n\x0f\x66\x61ult_tolerance\x18\x04 \x01(\x05\x12\x14\n\x0cpeer_servers\x18\x05 \x03(\t\"L\n\x15\x43onfigurationResponse\x12\x0f\n\x07success\x18\x01 \x01(\x08\x12\x0f\n\x07message\x18\x02 \x01(\t\x12\x11\n\ttimestamp\x18\x03 \x01(\x03\x42-\n\x0f\x63om.hasup.protoB\x12\x43onfigurationProto\xea\x02\x05Hasupb\x06proto3"

pool = Google::Protobuf::DescriptorPool.generated_pool
pool.add_serialized_file(descriptor_data)

module Hasup
  Configuration = ::Google::Protobuf::DescriptorPool.generated_pool.lookup("com.hasup.proto.Configuration").msgclass
  ConfigurationResponse = ::Google::Protobuf::DescriptorPool.generated_pool.lookup("com.hasup.proto.ConfigurationResponse").msgclass
end
