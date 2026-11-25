# ScreenX AI release rules. Media3, CameraX, Compose and DataStore ship their own consumer rules.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.jetbrains.annotations.**
# Services, receivers and activities are kept automatically from the manifest.
