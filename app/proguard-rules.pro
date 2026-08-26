# WA Al-othmany project rules
-keepattributes *Annotation*

# Shizuku UserService is instantiated reflectively by the Shizuku server.
-keep class com.alothmany.wa.system.shizuku.TurboUserService { *; }
-keep class com.alothmany.wa.system.shizuku.ITurboUserService$Stub { *; }
