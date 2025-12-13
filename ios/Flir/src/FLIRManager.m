// ObjC shim implementation forwarding to Swift `FlirManager` via runtime selectors
#import "FLIRManager.h"
#import <objc/message.h>

@implementation FLIRManager

+ (instancetype)shared {
    Class cls = NSClassFromString(@"FlirManager");
    if (!cls) return nil;
    SEL sel = sel_registerName("shared");
    if (![cls respondsToSelector:sel]) return nil;
    id (*msgSend0)(id, SEL) = (id (*)(id, SEL))objc_msgSend;
    return msgSend0((id)cls, sel);
}

- (BOOL)isAvailable {
    Class cls = NSClassFromString(@"FlirManager");
    SEL sel = sel_registerName("isSDKAvailable");
    if (!cls || ![cls respondsToSelector:sel]) return NO;
    BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
    return msgSend0((id)cls, sel);
}

- (double)getTemperatureAtPoint:(int)x y:(int)y {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("getTemperatureAt: y:");
    // Swift method name mangling may differ; fall back to method used by FlirModule
    SEL selAlt = sel_registerName("getTemperatureAtPoint:y:");
    SEL use = NULL;
    if (inst && [inst respondsToSelector:sel]) use = sel;
    if (inst && [inst respondsToSelector:selAlt]) use = selAlt;
    if (!inst || !use) return NAN;
    double (*msgSend2)(id, SEL, int, int) = (double (*)(id, SEL, int, int))objc_msgSend;
    return msgSend2(inst, use, x, y);
}

- (double)getTemperatureAtNormalized:(double)nx y:(double)ny {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("getTemperatureAtNormalized:y:");
    if (!inst || ![inst respondsToSelector:sel]) return NAN;
    double (*msgSend2)(id, SEL, double, double) = (double (*)(id, SEL, double, double))objc_msgSend;
    return msgSend2(inst, sel, nx, ny);
}

- (int)getBatteryLevel {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("getBatteryLevel");
    if (!inst || ![inst respondsToSelector:sel]) return -1;
    int (*msgSend0)(id, SEL) = (int (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

- (BOOL)isBatteryCharging {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("isBatteryCharging");
    if (!inst || ![inst respondsToSelector:sel]) return NO;
    BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

- (void)setPreferSdkRotation:(BOOL)prefer {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("setPreferSdkRotation:");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, BOOL) = (void (*)(id, SEL, BOOL))objc_msgSend;
    msgSend1(inst, sel, prefer);
}

- (BOOL)isPreferSdkRotation {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("isPreferSdkRotation");
    if (!inst || ![inst respondsToSelector:sel]) return NO;
    BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

- (nullable NSString *)latestFrameBase64 {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("latestFrameBase64");
    if (!inst || ![inst respondsToSelector:sel]) return nil;
    id (*msgSend0)(id, SEL) = (id (*)(id, SEL))objc_msgSend;
    return (NSString *)msgSend0(inst, sel);
}

- (void)retainClient:(NSString *)clientId {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("retainClient:");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, id) = (void (*)(id, SEL, id))objc_msgSend;
    msgSend1(inst, sel, clientId);
}

- (void)releaseClient:(NSString *)clientId {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("releaseClient:");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, id) = (void (*)(id, SEL, id))objc_msgSend;
    msgSend1(inst, sel, clientId);
}

- (void)setPalette:(NSString *)paletteName {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("setPalette:");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, id) = (void (*)(id, SEL, id))objc_msgSend;
    msgSend1(inst, sel, paletteName);
}

- (void)setPaletteFromAcol:(double)acol {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("setPaletteFromAcol:");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, double) = (void (*)(id, SEL, double))objc_msgSend;
    msgSend1(inst, sel, acol);
}

- (nullable NSString *)getPaletteNameFromAcol:(double)acol {
    Class cls = NSClassFromString(@"FlirManager");
    SEL sel = sel_registerName("getPaletteNameFromAcol:");
    if (!cls || ![cls respondsToSelector:sel]) return nil;
    id (*msgSend1)(id, SEL, double) = (id (*)(id, SEL, double))objc_msgSend;
    return (NSString *)msgSend1((id)cls, sel, acol);
}

- (nullable UIImage *)latestFrameImage {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("latestImage");
    SEL selAlt = sel_registerName("latestFrameImage");
    SEL use = NULL;
    if (inst && [inst respondsToSelector:selAlt]) use = selAlt;
    else if (inst && [inst respondsToSelector:sel]) use = sel;
    if (!inst || !use) return nil;
    id (*msgSend0)(id, SEL) = (id (*)(id, SEL))objc_msgSend;
    return (UIImage *)msgSend0(inst, use);
}

- (void)startDiscovery {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("startDiscovery");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend0)(id, SEL) = (void (*)(id, SEL))objc_msgSend;
    msgSend0(inst, sel);
}

- (void)stopDiscovery {
    id inst = [[self class] shared];
    SEL sel = sel_registerName("stopDiscovery");
    if (!inst || ![inst respondsToSelector:sel]) return;
    void (*msgSend0)(id, SEL) = (void (*)(id, SEL))objc_msgSend;
    msgSend0(inst, sel);
}

@end
