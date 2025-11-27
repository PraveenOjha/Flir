Pod::Spec.new do |s|
  s.name         = 'Flir'
  s.version      = '2.0.0'
  s.summary      = 'FLIR Thermal SDK React Native - On-Demand Download'
  s.homepage     = 'https://github.com/PraveenOjha/Flir'
  s.license      = { :type => 'MIT' }
  s.author       = { 'Praveen Ojha' => 'https://github.com/PraveenOjha' }
  s.source       = { :git => 'https://github.com/PraveenOjha/Flir.git', :tag => s.version.to_s }
  s.platform     = :ios, '13.0'
  s.swift_version = '5.0'

  s.source_files = [
    'ios/Flir/src/**/*.{h,m,mm,swift}',
    'ios/Flir/SDKLoader/**/*.{h,m,swift}'
  ]
  s.public_header_files = 'ios/Flir/src/**/*.h'
  
  s.resource_bundles = {
    'FlirSDKResources' => ['sdk-manifest.json']
  }
  
  s.frameworks = 'ExternalAccessory', 'Foundation', 'UIKit'
  
  s.pod_target_xcconfig = {
    'OTHER_LDFLAGS' => '-weak_framework ThermalSDK',
    'ENABLE_BITCODE' => 'NO'
  }
  
  s.dependency 'React-Core'
end
