Pod::Spec.new do |s|
  s.name         = 'Flir'
  s.version      = '0.1.0'
  s.summary      = 'FLIR Thermal SDK React Native iOS wrapper'
  s.license      = { :type => 'MIT' }
  s.platform     = :ios, '13.0'

  # Source files for the RN native module
  s.source_files = 'Flir/*.{h,m,mm}'

  # Vendored FLIR framework (checked into repo under flir/lib/ios)
  s.vendored_frameworks = 'ThermalSDK.framework'

  # Do not enforce a React dependency here; the app's Podfile should already include React
  # If needed, add: s.dependency 'React-Core'
end
