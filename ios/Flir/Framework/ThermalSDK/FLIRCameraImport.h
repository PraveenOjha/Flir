//
//  FLIRCameraImport.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//
//  Initialize and perform import (list and import images).

#import <UIKit/UIKit.h>

#import "FLIRIdentity.h"

/** Describes different possible file locations in the cameras filesystem. */
typedef NS_ENUM(NSInteger, FLIRLocation)
{
    /** Unknown root. */
    FLIRLocationUnknown,
    /** The active camera folder used for storing images. */
    FLIRLocationActive,
    /** Base folder for images (e.g. "DCIM" on the SD card). */
    FLIRLocationImageBase
};

/// Options for listing images
typedef NS_OPTIONS(NSInteger, FLIRListImagesFlags) {
    /// recurse all found folders
    FLIRListImagesRecursive = 1 << 0,
    /// include hidden files
    FLIRListImagesShowHidden = 1 << 1
};

/**
 *  Abstract file path on camera's filesystem.
 */
@interface FLIRFileReference : NSObject
/** Defines base part of the file path. */
@property (nonatomic, readwrite) FLIRLocation location;
/** Relative path to location.
 *
 * Example: Both "image.jpg" and "/image.jpg" would refer to an image file in the folder of `location`, and both "" and "/" would refer to the folder of `location`.
 * @note The path is expressed in a camera-compatible format using forward slashes (/) as the separator.
 */
@property (nonatomic, nonnull, readwrite) NSString *path;
@end

/**
 *  Abstract directory path on camera's filesystem.
 */
@interface FLIRFolderReference : NSObject
/** Defines base part of the file path. */
@property (nonatomic, readwrite) FLIRLocation location;
/** Relative path to location.
 *
 * @note The path is expressed in a camera-compatible format using forward slashes (/) as the separator.
 */
@property (nonatomic, nonnull, readwrite) NSString *path;

@end

@class FLIRThumbnail;

/**
 *  Delegate to return events when files are found on a camera.
 */
@protocol FLIRCameraImportEventDelegate <NSObject>

/**
 *  This is called when a file is imported from the camera to the device.
 *  Note: you should NOT try to call `FLIRCamera.disconnect()` directly from this callback. Instead i.e. spawn a new thread.
 *
 *  @param filename The path to a new file.
 */
- (void)fileAdded:(NSString * _Nonnull)filename;

/**
 *  This is called when error occurs when downloading a file.
 *  Note: you should NOT try to call `FLIRCamera.disconnect()` directly from this callback. Instead i.e. spawn a new thread.
 *
 *  @param e Dictionary with error information (error message and filename)
 */
- (void)importError:(NSDictionary<NSString *, NSString *> * _Nonnull)e;


/**
 *  This is called to show the progress of downloading image files from the device.
 *  Note: you should NOT try to call `FLIRCamera.disconnect()` directly from this callback. Instead i.e. spawn a new thread.
 *
 *  @param progress The number of bytes downloaded so far.
 *  @param total The total number of bytes of the file downloading.
 *  @param file   The reference of the file currently being downloaded. Remember to compare files using `isEqual`.
 */
- (void)fileProgress:(NSInteger)progress total:(NSInteger)total file:(FLIRFileReference * _Nonnull)file;

@optional

/**
 *  This is called when a thumbnail is downloaded
 *
 *  @param thumbnail A thumbnail with a reference to the original image and a thumbnail as a UIImage
 */
- (void)gotThumbnail:(FLIRThumbnail * _Nonnull)thumbnail;

/**
 * This is called when a file is deleted
 * @param file The reference of the file that was deleted
 */
- (void)fileDeleted:(FLIRFileReference * _Nonnull)file;

@end

/**
 *  Objects returned by listImages
 */

@interface  FLIRFileInfo : NSObject
/** The name of the file, without the path (like Unix's basename)  */
@property (nonatomic, nonnull) NSString *name;
/** Abstract path to the file. */
@property (nonatomic, nonnull) FLIRFileReference *reference;
/** The time this file was last changed */
@property (nonatomic, nonnull) NSDateComponents *time;
/** The file size in bytes */
@property (nonatomic) long long size;
/** True if this file object represents a directory, false otherwise */
@property (nonatomic) bool isDirectory;
@end

@interface FLIRThumbnail : NSObject
/** File reference */
@property (nonatomic, nonnull) FLIRFileReference *reference;
/** Thumbnail */
@property (nonatomic, nonnull) UIImage* thumbnail;
@end

/**
 *  This class facilitates importing images from Camera.
 */
@interface FLIRCameraImport : NSObject

/**
 *  Import files from camera.
 *
 *  @param imageList List of files to import. Use `listImages` or `listImagesInWorkFolder` to obtain an image list.
 *  @param destPath  Destination path.
 *
 *  @return TRUE if all is OK.
 */
- (BOOL)startImport:(NSArray<FLIRFileReference *> * _Nonnull)imageList withDestPath:(NSString * _Nonnull)destPath;

/**
 * Import thumbnails for files.
 *
 * @param imageList List of images. If a thumbnail is available, it will be imported, and the delegate will be called.
 */
- (void)startImportThumbnails:(NSArray<FLIRFileReference *> * _Nonnull)imageList;

/**
 *  Cancels the import session
 */
- (void)cancelImport;

/**
 *  A delegate to handle the events in FLIRCameraImportEventDelegate.
 */
@property (nonatomic, weak, nullable) id<FLIRCameraImportEventDelegate> delegate;

/**
 *  List all Flir files from default active folder in camera.
 *
 *  @return Array with FLIRFileInfos.
 */
-(NSArray<FLIRFileInfo *> * _Nullable)listImages:(out NSError *_Nullable *_Nullable)error;

/**
 *  List all Flir files in camera.
 *
 *  @param folder the folder to list, nil will use the active default folder
 *  @param flags flags for recursive listing and including hidden folder
 *
 *  @return Array with FLIRFileInfos.
 */
- (NSArray<FLIRFileInfo *> * _Nullable)listImages:(FLIRFileReference * _Nullable)folder
                                            flags:(FLIRListImagesFlags)flags
                                            error: (out NSError *_Nullable *_Nullable)error;

/**
 *  List all Flir files from folder in camera.
 *
 *  @param folder workfolder with files.
 *
 *  @return Array with FLIRFileInfos.
 */

- (NSArray<FLIRFileInfo *> * _Nullable)listImagesInWorkFolder:(FLIRFileReference * _Nonnull)folder error:(out NSError *_Nullable *_Nullable)error;

/**
 * Gets workfolders as a list of folders from the specified camera.
 *
 * @return Returns workfolders as a list of folders from the camera. List may be empty.
 */
- (NSArray<FLIRFileInfo *> * _Nullable)listWorkfolders:(out NSError *_Nullable *_Nullable)error;

- (void)startDeleteFiles:(NSArray<FLIRFileReference *> * _Nonnull)fileList;

@end
