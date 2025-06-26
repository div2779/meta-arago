SUMMARY = "Matter IoT connectivity on TI boards"
DESCRIPTION = "This recipe primes the matter environment"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

BRANCH = "v1.4-branch"
SRC_URI = "gitsm://github.com/project-chip/connectedhomeip.git;protocol=https;branch=${BRANCH};lfs=1"

SRCREV = "47ce873180594df123091f45e144fad2d476a395"


do_matter_bootstrap[network] = "1"
do_compile[network] = "1"

TARGET_CC_ARCH += "${LDFLAGS}"
DEPENDS += " glib-2.0 gn-native ninja-native avahi dbus-glib-native pkgconfig-native python3-native boost zap-native openssl-native ca-certificates-native clang-native"
RDEPENDS_${PN} += " libavahi-client openssl "
FILES:${PN} += "usr/share"

INSANE_SKIP:${PN} += "dev-so debug-deps strip"

PACKAGECONFIG ?= ""
PACKAGECONFIG[debug] = "is_debug=true,is_debug=false"

inherit pkgconfig

GN_TARGET_ARCH_NAME:aarch64 = "arm64"
GN_TARGET_ARCH_NAME:arm = "arm"
GN_TARGET_ARCH_NAME:x86 = "x86"
GN_TARGET_ARCH_NAME:x86-64 = "x64"


def gn_target_arch_name(d):
    """Returns a GN architecture name corresponding to the target machine's
    architecture."""
    name = d.getVar("GN_TARGET_ARCH_NAME")
    if name is None:
        bb.fatal('Unsupported target architecture. A valid override for the '
                 'GN_TARGET_ARCH_NAME variable could not be found.')
    return name

# this variable must use spaces and double quotes for parameter strings because
# *gn* is evil
GN_ARGS = " \
    ${PACKAGECONFIG_CONFARGS} \
    target_cpu="${@gn_target_arch_name(d)}" \
    target_arch="${TUNE_FEATURES}" \
    target_os="linux" \
    treat_warnings_as_errors=false \
    enable_rtti=true \
    enable_exceptions=true \
"

# Make sure pkg-config, when used with the host's toolchain to build the
# binaries we need to run on the host, uses the right pkg-config to avoid
# passing include directories belonging to the target.
GN_ARGS += 'host_pkg_config="pkg-config-native"'

S = "${WORKDIR}/git"

common_configure() {
    # this block must use spaces and double quotes for strings because *gn* is
    # evil
    PKG_CONFIG_SYSROOT_DIR=${PKG_CONFIG_SYSROOT_DIR} \
    PKG_CONFIG_LIBDIR=${PKG_CONFIG_PATH} \
    gn gen out/ --args='
        ${GN_ARGS}
        import("//build_overrides/build.gni")
        target_cflags=[
            "-DCHIP_DEVICE_CONFIG_WIFI_STATION_IF_NAME=\"wlan0\"",
            "-DCHIP_DEVICE_CONFIG_LINUX_DHCPC_CMD=\"udhcpc -b -i %s \"",
        ]
        custom_toolchain="${build_root}/toolchain/custom"
        target_cc="${CC}"
        target_cxx="${CXX}"
        target_ar="${AR}"
    '
}

export https_proxy
export http_proxy
export ftp_proxy
export no_proxy

do_matter_bootstrap() {
	cd "${S}"
	. ${S}/scripts/bootstrap.sh
}

do_configure() {
	. scripts/activate.sh
	pip install click

	cd ${S}/examples/chip-tool
	common_configure

	cd ${S}/examples/lock-app/linux
	common_configure

	cd ${S}/examples/thermostat/linux
	common_configure

	cd ${S}/examples/lighting-app/linux
	common_configure
}

do_compile() {
	. scripts/activate.sh

	cd ${S}/examples/chip-tool
	ninja -C out/

	cd ${S}/examples/lock-app/linux
	ninja -C out/

	cd ${S}/examples/thermostat/linux
	ninja -C out/

	cd ${S}/examples/lighting-app/linux
	ninja -C out/
}

do_install() {
	install -d -m 755 ${D}${bindir}

	# Install chip-tool
	install ${S}/examples/chip-tool/out/chip-tool ${D}${bindir}

	# lock-app
	install ${S}/examples/lock-app/linux/out/chip-lock-app ${D}${bindir}
	install ${S}/examples/thermostat/linux/out/thermostat-app ${D}${bindir}
	install ${S}/examples/lighting-app/linux/out/chip-lighting-app ${D}${bindir}
}

addtask matter_bootstrap after do_unpack before do_configure

INSANE_SKIP_${PN} = "ldflags"