require 'rails/railtie'

module Platform

    class ApplicationModules

        def self.get(module_name)
            module_name = module_name.to_s.camelize
            module_name << 'Module' unless module_name.end_with?('Module')
            self.modules[module_name]
        end

        def self.modules
            com.proofpoint.rack.Main.application_modules
        end

    end

    def self.method_missing(method_name, method_args)
        if method_args.empty?
            mod = ApplicationModules.get(:method_name)
            mod || super
        else
            super
        end
    end
end
