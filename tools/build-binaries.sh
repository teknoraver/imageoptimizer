#!/bin/sh
# Rebuilds the native optimizers shipped in app/src/main/jniLibs/<abi>/:
#   libjpegoptim.so  jpegoptim, statically linked with libjpeg-turbo
#   liboxipng.so     oxipng
# They are plain executables named lib*.so so that the OS extracts them to
# nativeLibraryDir, the only app-private location SELinux allows exec from.
#
# Prerequisites: Android NDK, cmake, nasm (x86_64 SIMD), git, and a rustup
# toolchain with the *-linux-android targets installed:
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

set -eu

NDK=${NDK:-$HOME/Android/Sdk/ndk/29.0.14206865}
API=${API:-23}
JPEGOPTIM_SRC=${JPEGOPTIM_SRC:-$HOME/src/jpegoptim}
OXIPNG_SRC=${OXIPNG_SRC:-$HOME/src/android/oxipng}
LIBJPEG_TURBO_TAG=${LIBJPEG_TURBO_TAG:-3.2.0}
ABIS=${ABIS:-"arm64-v8a armeabi-v7a x86_64"}

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JNILIBS=$ROOT/app/src/main/jniLibs
BUILD=${BUILD:-${TMPDIR:-/tmp}/imageoptimizer-build}
TOOLCHAIN=$(echo "$NDK"/toolchains/llvm/prebuilt/*)
NPROC=$(nproc)

command -v nasm >/dev/null || echo "warning: nasm not found, x86_64 libjpeg-turbo will be built without SIMD" >&2

mkdir -p "$BUILD"
if [ ! -d "$BUILD/libjpeg-turbo" ]; then
	git clone --depth 1 --branch "$LIBJPEG_TURBO_TAG" \
		https://github.com/libjpeg-turbo/libjpeg-turbo "$BUILD/libjpeg-turbo"
fi

for abi in $ABIS; do
	case $abi in
	arm64-v8a)	triple=aarch64-linux-android;	clang=aarch64-linux-android ;;
	armeabi-v7a)	triple=armv7-linux-androideabi;	clang=armv7a-linux-androideabi ;;
	x86_64)		triple=x86_64-linux-android;	clang=x86_64-linux-android ;;
	*)		echo "unknown ABI $abi" >&2; exit 1 ;;
	esac

	echo "=== $abi ==="
	cmake_android="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
		-DANDROID_ABI=$abi -DANDROID_PLATFORM=android-$API -DCMAKE_BUILD_TYPE=Release"
	prefix=$BUILD/prefix/$abi

	# libjpeg-turbo, static only
	cmake -S "$BUILD/libjpeg-turbo" -B "$BUILD/libjpeg-turbo-$abi" $cmake_android \
		-DCMAKE_INSTALL_PREFIX="$prefix" -DCMAKE_INSTALL_LIBDIR=lib \
		-DENABLE_SHARED=0 -DWITH_TURBOJPEG=0
	cmake --build "$BUILD/libjpeg-turbo-$abi" -j"$NPROC"
	cmake --install "$BUILD/libjpeg-turbo-$abi"

	# jpegoptim
	cmake -S "$JPEGOPTIM_SRC" -B "$BUILD/jpegoptim-$abi" $cmake_android \
		-DUSE_MOZJPEG=0 \
		-DLIBJPEG_INCLUDE_DIR="$prefix/include" -DLIBJPEG_LIBRARY="$prefix/lib/libjpeg.a"
	cmake --build "$BUILD/jpegoptim-$abi" -j"$NPROC"
	"$TOOLCHAIN/bin/llvm-strip" -o "$JNILIBS/$abi/libjpegoptim.so" \
		"$(find "$BUILD/jpegoptim-$abi" -type f -name jpegoptim | head -n1)"

	# oxipng
	TRIPLE_ENV=$(echo "$triple" | tr 'a-z-' 'A-Z_')
	triple_us=$(echo "$triple" | tr '-' '_')
	env \
		"CARGO_TARGET_${TRIPLE_ENV}_LINKER=$TOOLCHAIN/bin/${clang}${API}-clang" \
		"CC_${triple_us}=$TOOLCHAIN/bin/${clang}${API}-clang" \
		"AR_${triple_us}=$TOOLCHAIN/bin/llvm-ar" \
		CARGO_PROFILE_RELEASE_STRIP=true \
		CARGO_TARGET_DIR="$BUILD/oxipng-target" \
		cargo build --release --locked --manifest-path "$OXIPNG_SRC/Cargo.toml" --target "$triple"
	cp "$BUILD/oxipng-target/$triple/release/oxipng" "$JNILIBS/$abi/liboxipng.so"
	chmod 755 "$JNILIBS/$abi/libjpegoptim.so" "$JNILIBS/$abi/liboxipng.so"
done

file "$JNILIBS"/*/*
