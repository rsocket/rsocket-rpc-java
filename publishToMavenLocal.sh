NOW=$(date +%s)
echo "versionSuffix: -$NOW"
./gradlew build publishToMavenLocal -x test -x verifyGoogleJavaFormat -c settings.jitpack.gradle -PversionSuffix="-$NOW" "$@"
