# Compose / Kotlin 元数据
-dontwarn org.jetbrains.annotations.**
-keep class kotlin.Metadata { *; }

# 保留下四大组件的入口类
-keep class com.focuslock.app.service.** { *; }
-keep class com.focuslock.app.ui.LockActivity { *; }
-keep class com.focuslock.app.ui.MainActivity { *; }

# 无障碍服务通过反射实例化，必须保留
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
