#!/system/bin/sh

mobile_platform_version="4.3"
mobile_device_type="Android-x86"
mobile_device_uniqueid="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

# delete the csdXXXXXX temp file so they don't start piling up
rm -f $1

exec curl \
	--insecure \
	--user-agent "AnyConnect Android 3.0.09156" \
	--header "X-Transcend-Version: 1" \
	--header "X-Aggregate-Auth: 1" \
	--header "X-AnyConnect-Identifier-Platform: android" \
	--header "X-AnyConnect-Identifier-PlatformVersion: $mobile_platform_version" \
	--header "X-AnyConnect-Identifier-DeviceType: $mobile_device_type" \
	--header "X-AnyConnect-Identifier-Device-UniqueID: $mobile_device_uniqueid" \
	--cookie "sdesktop=$CSD_TOKEN" \
	--data-ascii @- "https://$CSD_HOSTNAME/+CSCOE+/sdesktop/scan.xml" <<-END
endpoint.feature="failure";
endpoint.os.version="Android";
END
