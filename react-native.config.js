module.exports = {
    dependency: {
        platforms: {
            android: {
                sourceDir: './android/Flir',
                packageImportPath: 'import flir.android.FlirPackage;',
                packageInstance: 'new FlirPackage()',
            },
            ios: {
                podspecPath: './Flir.podspec',
            },
        },
    },
};
