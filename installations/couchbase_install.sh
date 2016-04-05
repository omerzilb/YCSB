#!/bin/bash -ex

# NOTE: firewall must allow ports 8091 (config) and 11210 (client-node bucket without SSL)

usage()
{
	cat<<-EOF
	USAGE: ${0##*/} <cluster-ramsize-mb> <cluster-index-ramsize-mb> <mount-point> <user> <password> <enable-flush> <bucket-type>

	cluster-ramsize-mb       - total RAM allocated for Couchbase Data in MB.
	cluster-index-ramsize-mb - total RAM allocated for Couchbase Index in MB.
	mount-point              - must exist
	user                     - username to be used for cluster, node and bucket creation and editing
	password                 - password to be used for cluster, node and bucket creation and editing
	enable-flush             - allows bucket/data deletion. Should be 0/1.
	bucket-type              - couchbase or memcached

	Best practices for ramsize would be to allocate half the machine's available memory to Couchbase,
	divided between data and index. A common practice for a 64GB machine is to set:
		cluster-ramsize-mb=38572
	    cluster-index-ramsize-mb=256

	Must be run as root.
	EOF

	exit 1
}

#must be run as root
[[ $UID -ne 0 ]] && usage
[[ -z "$7" ]] && usage


CLUSTER_RAMSIZE=$1  # MB about half of system memory
CLUSTER_INDEX_RAMSIZE=$2
MOUNT_POINT=$3

USER=$4
PASSWORD=$5

DATA_DIR=${MOUNT_POINT}/data
INDEX_DIR=${MOUNT_POINT}/index

ENABLE_FLUSH=$6
BUCKET_TYPE=$7

CLI=/opt/couchbase/bin/couchbase-cli


if ! [[ -d ${MOUNT_POINT} ]]; then
	echo "${MOUNT_POINT} does not exist, cannot proceed with couchbase installation"
	exit 1
fi

# Some notes from installation
#Warning: Transparent hugepages looks to be active and should not be.
#Please look at http://bit.ly/1hTySfg as for how to PERMANENTLY alter this setting.
#Warning: Swappiness is not set to 0.
#Please look at http://bit.ly/1hTySfg as for how to PERMANENTLY alter this setting.
#
#Please note that you have to update your firewall configuration to
#allow connections to the following ports: 11211, 11210, 11209, 4369,
#8091, 8092, 8093, 9100 to 9105, 9998, 18091, 18092, 11214, 11215 and
#from 21100 to 21299.
#
#(omzg: though for single-node config, 11210/tcp and 8091/tcp are sufficient)

# on CentOS, the service won't autostart, so let's use the service file directly anyway
# https://forums.couchbase.com/t/centos-7-couchbase-server-cannot-start-the-service/6261

COUCHBASE_RPM=/opt/couchbase-server-community-4.0.0-centos7.x86_64.rpm
COUCHBASE_SERVICE_FILE=/opt/couchbase/etc/couchbase_init.d

if [[ -e ${COUCHBASE_SERVICE_FILE} ]]; then
	echo "Couchbase already installed, run couchbase_uninstall.sh first"
	exit 1
fi

yum install -y ${COUCHBASE_RPM}
systemctl disable couchbase-server || :
systemctl stop couchbase-server || :
${COUCHBASE_SERVICE_FILE} start


# init node
mkdir -p ${DATA_DIR} ${INDEX_DIR}
chown -R couchbase:couchbase ${DATA_DIR} ${INDEX_DIR}
sleep 20
${CLI} node-init -c localhost -u ${USER} -p ${PASSWORD} -d --node-init-data-path=${DATA_DIR} --node-init-index-path=${INDEX_DIR}

# init cluster
${CLI} cluster-init -c localhost --cluster-username=${USER} --cluster-password=${PASSWORD} --cluster-ramsize=${CLUSTER_RAMSIZE} --cluster-index-ramsize=${CLUSTER_INDEX_RAMSIZE} --services=data,index,query

# create bucket
${CLI} bucket-create -c localhost -u ${USER} -p ${PASSWORD} --bucket=default --bucket-type=${BUCKET_TYPE} --bucket-ramsize=${CLUSTER_RAMSIZE} --bucket-replica=0 --enable-flush=${ENABLE_FLUSH} --wait
