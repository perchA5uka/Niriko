# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in proguard-android-optimize.txt

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
