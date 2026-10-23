# Lumenkin ProGuard rules
# Keep WallpaperService and related classes
-keep class com.lumenkin.wallpaper.** { *; }
-keep class * extends android.service.wallpaper.WallpaperService { *; }
