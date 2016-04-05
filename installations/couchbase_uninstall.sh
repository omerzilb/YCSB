#!/bin/bash -ex

usage()
{
	cat<<-EOF
	USAGE: ${0##*/}

	Must be run as root.
	EOF

	exit 1
}

#must be run as root
[[ $UID -ne 0 ]] && usage

COUCHBASE_PACKAGE=couchbase-server-community
COUCHBASE_SERVICE_FILE=/opt/couchbase/etc/couchbase_init.d

if [[ -e ${COUCHBASE_SERVICE_FILE} ]]; then
	echo "Stopping and removing couchbase"
	/opt/couchbase/etc/couchbase_init.d stop || :
	yum erase -y $COUCHBASE_PACKAGE
fi
