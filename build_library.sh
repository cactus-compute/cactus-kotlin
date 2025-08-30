echo "Building Cactus Kotlin Multiplatform Library"

echo "Cleaning previous builds..."
./gradlew clean --no-daemon --no-configuration-cache

echo "Building KMP library..."
./gradlew build --no-daemon --no-configuration-cache

echo "Publishing to Maven Local (triggers release XCFramework build with embedded C++)..."
./gradlew publishToMavenLocal --no-daemon --no-configuration-cache