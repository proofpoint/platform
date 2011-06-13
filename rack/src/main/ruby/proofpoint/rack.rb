#
# Copyright 2010 Proofpoint, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
require 'java'
require 'rubygems'
require 'rack'
require 'rack/rewindable_input'
require 'forwardable'

module Proofpoint
  module RackServer
    class Builder
      def build(filename)
        rack_app, options_ignored = Rack::Builder.parse_file filename
        # Sets up the logger for rails apps
        if rack_app.respond_to? :configure
          begin
            rack_app.configure { Rails.logger = RackLogger.new }
          rescue
            # Ignore exceptions here, if we can't set the logger like this, it isn't rails, and therefore it will properly use the rack logger.
          end
        end
        return ServletAdapter.new(rack_app)
      end
    end

    class ServletAdapter
      def initialize(app)
        @app = app
        @logger = RackLogger.new
        @errors = java::lang::System::err.to_io # TODO: write to logger
      end

      def call(servlet_request, servlet_response)
        rack_env = {
                'rack.version' => Rack::VERSION,
                'rack.multithread' => true,
                'rack.multiprocess' => false,
                'rack.input' => Rack::RewindableInput.new(servlet_request.input_stream.to_io),
                'rack.errors' => @errors,
                'rack.logger' => @logger,
                'rack.url_scheme' => servlet_request.scheme,
                'REQUEST_METHOD' => servlet_request.method,
                'SCRIPT_NAME' => '',
                'PATH_INFO' => servlet_request.path_info,
                'QUERY_STRING' => (servlet_request.query_string || ""),
                'SERVER_NAME' => servlet_request.server_name,
                'SERVER_PORT' => servlet_request.server_port.to_s
        }

        rack_env['CONTENT_TYPE'] = servlet_request.content_type unless servlet_request.content_type.nil?
        rack_env['CONTENT_LENGTH']  = servlet_request.content_length unless servlet_request.content_length.nil?

        servlet_request.header_names.reject { |name| name =~ /^Content-(Type|Length)$/i }.each do |name|
          rack_env["HTTP_#{name.upcase.gsub(/-/,'_')}"] = servlet_request.get_headers(name).to_a.join(',')
        end

        response_status, response_headers, response_body = @app.call(rack_env)

        servlet_response.status = response_status
        response_headers.each do |header_name, header_value|
          case header_name
            when /^Content-Type$/i
              servlet_response.content_type = header_value.to_s
            when /^Content-Length$/i
              servlet_response.content_length = header_value.to_i
            else
              servlet_response.add_header(header_name.to_s, header_value.to_s)
          end
        end

        response_stream = servlet_response.output_stream
        response_body.each { |part| response_stream.write(part.to_java_bytes) }
        return response_stream.flush
      end
    end

    class RackLogger
      extend Forwardable

      def_delegators :get_logger, :debug, :info, :warn, :error
      alias_method :fatal, :error

      def get_logger
        call_stack = caller(0)
        this_file = call_stack.first.split(':').first
        caller_call = call_stack.reject { |call| call =~ /#{this_file}\:|Forwardable/i }.first
        return com::proofpoint::log.Logger.get(caller_call.split(':').first.split('/').last + ":" + caller_call.split('`').last.chomp("'"))
      end

      def debug?
        return get_logger.is_debug_enabled
      end

      def info?
        return get_logger.is_info_enabled
      end

      def warn?
        return true
      end
      alias_method :error?, :warn?
      alias_method :fatal?, :error?

    end
  end
end
