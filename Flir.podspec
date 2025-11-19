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
  s.source_files = 'ios/Flir/src/**/*.{h,m,mm}'
  s.public_header_files = 'ios/Flir/src/**/*.h'

  # Vendored FLIR framework and other binary libs (placed in ios/Flir/libs)
  # When publishing, place ThermalSDK.framework and any supporting frameworks
  # or .dylib packages under `ios/Flir/libs/` in this repo.
  # Vendored frameworks are expected under ios/Flir/libs to match the new folder naming
  s.vendored_frameworks = 'ios/Flir/libs/*.framework'
  s.vendored_libraries = 'ios/Flir/libs/*.dylib'

  # System frameworks to link against
  s.frameworks = 'ExternalAccessory', 'Foundation', 'UIKit'

  # Keep vendored libs path so CocoaPods includes them in the pod archive
  s.preserve_paths = 'ios/Flir/libs/*'
  
  # React Native dependency
  s.dependency 'React-Core'
end
