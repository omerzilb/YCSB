.PHONY: all ycsb couchbase

OS:=$(shell grep "^ID=" /etc/os-release | awk -F"=" '{print $$2}')
ifeq ($(OS),ubuntu)
	PM:=apt-get
else
	PM:=yum
endif

COUCHBASE_VER:=4.0.0
COUCHBASE_RPM:=couchbase-server-community-$(COUCHBASE_VER)-centos7.x86_64.rpm
COUCHBASE_TARGET:=/opt
COUCHBASE_FILE:=$(COUCHBASE_TARGET)/$(COUCHBASE_RPM)

all: ycsb $(COUCHBASE_FILE)

ycsb:
	$(PM) install -y maven
	mvn clean package

$(COUCHBASE_FILE):
	wget -P ${COUCHBASE_TARGET} http://packages.couchbase.com/releases/$(COUCHBASE_VER)/$(COUCHBASE_RPM)
