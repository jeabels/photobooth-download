# Room, WorkManager and Firebase ship their own consumer rules.
# Firestore data is written and read as plain Maps (no reflection-based POJO mapping),
# so no model classes need to be kept.

# Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
