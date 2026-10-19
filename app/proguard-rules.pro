# CaptureConnection compares BankNotificationListener::class.java.name with Settings.Secure enabled_notification_listeners,
# so the class name must not be obfuscated.
-keepnames class br.com.fluxo.app.BankNotificationListener
# Other enums persisted by name use valueOf (kept by R8); components come from AndroidManifest.xml; org.json is the platform's.
