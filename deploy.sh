# Build the jar locally
./gradlew.bat clean build shadowJar --no-daemon

# Copy the built jar directly to your local Modrinth instance mods folder
cp build/libs/antilogout-2.1.0.jar "/c/Users/Hunkulous/AppData/Roaming/ModrinthApp/profiles/ThaSMP/mods/"

echo "Successfully deployed antilogout-2.1.0.jar to local ThaSMP profile!"