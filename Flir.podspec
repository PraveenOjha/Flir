Pod::Spec.new do |s|
  s.name         = 'Flir'
  s.version      = '2.2.31'
  s.summary      = 'FLIR Thermal SDK React Native - Bundled via postinstall'
  s.description  = <<-DESC
    A React Native wrapper for the FLIR Thermal SDK, providing thermal imaging
    capabilities for iOS applications. Supports discovery, connection, and 
    streaming from FLIR ONE and other FLIR thermal cameras.
    
    Can be built with or without the FLIR SDK using the FLIR_ENABLED flag.
  DESC
  s.homepage     = 'https://github.com/PraveenOjha/Flir'
  s.license      = { :type => 'MIT' }
  s.author       = { 'Praveen Ojha' => 'https://github.com/PraveenOjha' }
  s.source       = { :git => 'https://github.com/PraveenOjha/Flir.git', :tag => s.version.to_s }
  s.platform     = :ios, '13.0'
  s.swift_version = '5.0'

  # Source files
  s.source_files = [
    'ios/Flir/src/**/*.{h,m,mm,swift}',
    'ios/Flir/SDKLoader/**/*.{h,m,swift}'
  ]
  s.public_header_files = 'ios/Flir/src/**/*.h'
  
  # Resource bundles for SDK manifest
  s.resource_bundles = {
    'FlirSDKResources' => ['sdk-manifest.json']
  }
  
  # System frameworks
  s.frameworks = 'ExternalAccessory', 'Foundation', 'UIKit', 'Metal', 'MetalKit'
  
  # React Native dependency
  s.dependency 'React-Core'

  # Ensure React headers are available to this Pod across various RN/CocoaPods layouts
  # (helps when headers are in different public/private/ReactCore locations or when using use_frameworks)
  begin
    existing = s.pod_target_xcconfig rescue {}
  end
  existing_hdrs = existing.is_a?(Hash) ? (existing['HEADER_SEARCH_PATHS'] || '$(inherited)') : '$(inherited)'
  hdrs = [existing_hdrs,
          '"${PODS_ROOT}/Headers/Public/React"',
          '"${PODS_ROOT}/Headers/Public/React-Core"',
          '"${PODS_ROOT}/Headers/Public/ReactCommon"',
          '"${PODS_ROOT}/Headers/Public/React-CoreModules"'].join(' ')
  begin
    s.pod_target_xcconfig = (existing || {}).merge('HEADER_SEARCH_PATHS' => hdrs)
  rescue NoMethodError
    # Fallback for older CocoaPods that don't support pod_target_xcconfig
    s.user_target_xcconfig = (s.user_target_xcconfig || {}).merge('HEADER_SEARCH_PATHS' => hdrs)
  end
  
  
  # ==========================================================================
  # FLIR SDK CONFIGURATION
  # ==========================================================================
  # 
  # By default, this pod builds WITH FLIR SDK support. To build WITHOUT the
  # FLIR SDK (for development on machines without paid developer license):
  #
  # Option 1: Environment variable
  #   FLIR_DISABLED=1 pod install
  #
  # Option 2: In your app's Podfile, before `use_native_modules!`:
  #   ENV['FLIR_DISABLED'] = '1'
  #
  # When FLIR is disabled, the module provides fallback stub implementations
  # that return placeholder images and mock temperature data.
  # ==========================================================================
  
  flir_disabled = ENV['FLIR_DISABLED'] == '1' || ENV['FLIR_DISABLED'] == 'true'
  
  if flir_disabled
    puts "[Flir.podspec] ⚠️  Building WITHOUT FLIR SDK (FLIR_DISABLED=1)"
    
    s.pod_target_xcconfig = {
      'ENABLE_BITCODE' => 'NO',
      'GCC_PREPROCESSOR_DEFINITIONS' => '$(inherited) FLIR_DISABLED=1',
      'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited)',
      'OTHER_SWIFT_FLAGS' => '$(inherited)'
    }
  else
    puts "[Flir.podspec] ✅ Building WITH FLIR SDK support"
    
    # Check if ThermalSDK.xcframework exists in the SDK folder
    sdk_framework_path = File.join(__dir__, 'ios', 'Flir', 'Frameworks', 'ThermalSDK.xcframework')
    sdk_available = File.exist?(sdk_framework_path)
    
    if sdk_available
      puts "[Flir.podspec] ✅ ThermalSDK.xcframework found"
      
      # Vendored frameworks - the actual SDK
      s.vendored_frameworks = [
        'ios/Flir/Frameworks/ThermalSDK.xcframework',
        'ios/Flir/Frameworks/MeterLink.xcframework',
        'ios/Flir/Frameworks/libavcodec.62.dylib.xcframework',
        'ios/Flir/Frameworks/libavdevice.62.dylib.xcframework', 
        'ios/Flir/Frameworks/libavfilter.11.dylib.xcframework',
        'ios/Flir/Frameworks/libavformat.62.dylib.xcframework',
        'ios/Flir/Frameworks/libavutil.60.dylib.xcframework',
        'ios/Flir/Frameworks/liblive666.dylib.xcframework',
        'ios/Flir/Frameworks/libswresample.6.dylib.xcframework',
        'ios/Flir/Frameworks/libswscale.9.dylib.xcframework'
      ].select { |f| File.exist?(File.join(__dir__, f)) }
      
      s.pod_target_xcconfig = {
        'ENABLE_BITCODE' => 'NO',
        'ENABLE_LIBRARY_VALIDATION' => 'NO',
        'LD_RUNPATH_SEARCH_PATHS' => '$(inherited) @executable_path/Frameworks @loader_path/Frameworks',
        'GCC_PREPROCESSOR_DEFINITIONS' => '$(inherited) FLIR_SDK_AVAILABLE=1',
        'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) FLIR_ENABLED',
        'OTHER_SWIFT_FLAGS' => '$(inherited) -DFLIR_ENABLED',
        'HEADER_SEARCH_PATHS' => '$(inherited) "$(PODS_TARGET_SRCROOT)/ios/Flir/Framework"'
      }
    else
      puts "[Flir.podspec] ⚠️  ThermalSDK.xcframework NOT found - using header stubs only"
      puts "[Flir.podspec] To enable full SDK, copy FLIR SDK xcframeworks to ios/Flir/Frameworks/"
      
      # Use weak linking to allow compilation without actual framework
      s.pod_target_xcconfig = {
        'ENABLE_BITCODE' => 'NO',
        'OTHER_LDFLAGS' => '-weak_framework ThermalSDK',
        'HEADER_SEARCH_PATHS' => '$(inherited) "$(PODS_TARGET_SRCROOT)/ios/Flir/Framework"',
        'GCC_PREPROCESSOR_DEFINITIONS' => '$(inherited)',
        'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited)',
        'OTHER_SWIFT_FLAGS' => '$(inherited)'
      }
    end
  end
  
  # User target configuration
  s.user_target_xcconfig = {
    'ENABLE_BITCODE' => 'NO'
  }
  
  # Additional notes for integrating the SDK
  s.description = <<-DESC
    FLIR Thermal SDK React Native wrapper for iOS.
    
    INSTALLATION:
    1. Install via npm: npm install ilabs-flir
    2. Run pod install in your iOS directory
    
    SDK SETUP:
    The FLIR ThermalSDK is not included in the repo due to licensing, but this package
    provides a `postinstall` script that can download official release artifacts and
    place them into `ios/Flir/Frameworks/` and `android/Flir/libs/` during npm install.
    To enable full functionality manually:
    1. Download ThermalSDK.xcframework from FLIR
    2. Copy to node_modules/ilabs-flir/ios/Flir/Frameworks/
    3. Run pod install again
    
    BUILDING WITHOUT SDK:
    Set FLIR_DISABLED=1 before pod install to build without SDK.
    This enables development without a paid FLIR developer license.
  DESC
end
