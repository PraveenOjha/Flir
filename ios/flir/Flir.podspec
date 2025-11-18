Pod::Spec.new do |s|
  s.name         = 'Flir'
  s.version      = '1.0.0'
  s.summary      = 'FLIR Thermal SDK React Native iOS wrapper'
  s.description  = 'A React Native wrapper for the FLIR Thermal SDK, providing thermal imaging capabilities for iOS applications.'
  s.homepage     = 'https://github.com/PraveenOjha/Flir'
  s.license      = { :type => 'MIT' }
  s.author       = { 'Praveen Ojha' => 'https://github.com/PraveenOjha' }
  s.source       = { :git => 'https://github.com/PraveenOjha/Flir.git', :tag => s.version.to_s }
  s.platform     = :ios, '13.0'

  # Source files for the RN native module
  s.source_files = 'Flir/*.{h,m,mm}'

  # Vendored FLIR framework (checked into repo under flir/lib/ios)
  s.vendored_frameworks = 'ThermalSDK.framework'

  # Do not enforce a React dependency here; the app's Podfile should already include React
  # If needed, add: s.dependency 'React-Core'
end
