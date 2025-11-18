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

  # Paths relative to repository root where podspec is located
  s.source_files = 'ios/flir/Flir/**/*.{h,m,mm}'
  s.public_header_files = 'ios/flir/Flir/**/*.h'

  # Vendored FLIR framework
  s.vendored_frameworks = 'ios/flir/ThermalSDK.framework'

  # Framework search paths
  s.frameworks = 'Foundation', 'UIKit'
  
  # React Native dependency
  s.dependency 'React-Core'
end
