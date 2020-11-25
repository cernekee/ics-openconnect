#!/system/bin/sh

# These value may need to be extracted from the official HIP report, if made-up values are not accepted.
PLATFORM_VERSION="4.3"
PLATFORM_NAME="Android-x86"
HOSTID="deadbeef-dead-beef-dead-beefdeadbeef"

# Read command line arguments into variables
COOKIE=
IP=
MD5=

while [ "$1" ]; do
    if [ "$1" = "--cookie" ];    then shift; COOKIE="$1"; fi
    if [ "$1" = "--client-ip" ]; then shift; IP="$1"; fi
    if [ "$1" = "--md5" ];       then shift; MD5="$1"; fi
    shift
done

if [ -z "$COOKIE" -o -z "$IP" -o -z "$MD5" ]; then
    echo "Parameters --cookie, --client-ip, and --md5 are required" >&2
    exit 1
fi

# Extract username and domain and computer from cookie
USER=$(echo "$COOKIE" | sed -rn 's/(.+&|^)user=([^&]+)(&.+|$)/\2/p')
DOMAIN=$(echo "$COOKIE" | sed -rn 's/(.+&|^)domain=([^&]+)(&.+|$)/\2/p')
COMPUTER=$(echo "$COOKIE" | sed -rn 's/(.+&|^)computer=([^&]+)(&.+|$)/\2/p')

# Timestamp in the format expected by GlobalProtect server
NOW=$(date +'%m/%d/%Y %H:%M:%S')

log OPENCONNECT GP SCRIPT BEFORE TIMID ECHO

echo '   '

# FIXME: Doesn't work as a here-doc (cat <<EOF) with Android /system/bin/sh
echo '<hip-report name="hip-report">'
echo "	<md5-sum>$MD5</md5-sum>"
echo "	<user-name>$USER</user-name>"
echo "	<domain>$DOMAIN</domain>"
echo "	<host-name>$COMPUTER</host-name>"
echo "	<host-id>$HOSTID</host-id>"
echo "	<ip-address>$IP</ip-address>"
echo '	<ipv6-address></ipv6-address>'
echo "	<generate-time>$NOW</generate-time>"
echo '	<categories>'
echo '		<entry name="host-info">'
echo '			<client-version>4.0.2-19</client-version>'
echo "			<os>$PLATFORM_NAME $PLATFORM_VERSION</os>"
echo '			<os-vendor>Google</os-vendor>'
echo "			<domain>$DOMAIN.internal</domain>"
echo "			<host-name>$COMPUTER</host-name>"
echo "			<host-id>$HOSTID</host-id>"
echo '		</entry>'
echo '	</categories>'
echo '</hip-report>'
