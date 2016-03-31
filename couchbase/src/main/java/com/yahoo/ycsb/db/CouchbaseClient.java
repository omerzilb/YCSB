/**
 * Copyright (c) 2013 Yahoo! Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License. See accompanying LICENSE file.
 *
 * Submitted by Michael Nitschinger, 2015.
 */
package com.yahoo.ycsb.db;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.PersistTo;
import com.couchbase.client.java.ReplicateTo;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.StringByteIterator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

public class CouchbaseClient extends DB {

    private static final int OK = 0;
    private static final int ERROR = -1;

    private static final String DEFAULT_HOSTNAME = "localhost";
    private static final String DEFAULT_BUCKET = "default";
    private static final String DEFAULT_PASSWORD = "";

    private Bucket bucket;
    private Cluster cluster;
    private static CouchbaseEnvironment environment;
    private static PersistTo persistTo;
    private static ReplicateTo replicateTo;

    private static final Object clientLock = new Object();
    private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

    @Override
    public void init() throws DBException {
        INIT_COUNT.incrementAndGet();
        synchronized (clientLock) {
            if (environment == null) {
                environment = DefaultCouchbaseEnvironment.builder().build();
            }
        }
        Properties props = getProperties();

        String hostname = props.getProperty("couchbase.hostname", DEFAULT_HOSTNAME);
        String bucketName = props.getProperty("couchbase.bucket", DEFAULT_BUCKET);
        String password = props.getProperty("couchbase.password", DEFAULT_PASSWORD);
        persistTo = parsePersistTo(props.getProperty("couchbase.persistTo", "master"));
        replicateTo = parseReplicateTo(props.getProperty("couchbase.replicateTo", "0"));

        cluster = CouchbaseCluster.create(environment, hostname);
        bucket = cluster.openBucket(bucketName, password);
    }

    @Override
    public void cleanup() {
        cluster.disconnect();
        if (INIT_COUNT.decrementAndGet() == 0) {
            environment.shutdown();
        }
    }

    private static PersistTo parsePersistTo(final String persistTo) {
        if (persistTo.toLowerCase().equals("master")) {
            return PersistTo.MASTER;
        } else if (persistTo.equals("1")) {
            return PersistTo.ONE;
        } else if (persistTo.equals("2")) {
            return PersistTo.TWO;
        } else if (persistTo.equals("3")) {
            return PersistTo.THREE;
        } else if (persistTo.equals("4")) {
            return PersistTo.FOUR;
        } else {
            return PersistTo.NONE;
        }
    }

    private static ReplicateTo parseReplicateTo(final String replicateTo) {
        if (replicateTo.equals("1")) {
            return ReplicateTo.ONE;
        } else if (replicateTo.equals("2")) {
            return ReplicateTo.TWO;
        } else if (replicateTo.equals("3")) {
            return ReplicateTo.THREE;
        } else {
            return ReplicateTo.NONE;
        }
    }

    @Override
    public Status read(final String table, final String key, final Set<String> fields,
                       final HashMap<String, ByteIterator> result) {
        try {
            String id = generateId(table, key);
            JsonDocument foundDocument = bucket.get(id);
            if (foundDocument == null) {
                System.out.println("Key not found, please check loaded data: " + key);
                return Status.ERROR;
            }

            JsonObject content = foundDocument.content();
            if (fields == null) {
                for (String name : content.getNames()) {
                    result.put(name, new ByteArrayByteIterator(content.getString(name).getBytes()));
                }
            } else {
                for (String field : fields) {
                    result.put(field, new ByteArrayByteIterator(content.getString(field).getBytes()));
                }
            }

            return Status.OK;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error reading key: " + key);
            return Status.ERROR;
        }
    }

    @Override
    public Status scan(final String table, final String startkey, final int recordcount,
	    final Set<String> fields, final Vector<HashMap<String, ByteIterator>> result) {
        throw new IllegalStateException("Range scan will be implemented soon (view/n1ql).");
    }

    @Override
    public Status update(final String table, final String key, final HashMap<String, ByteIterator> values) {
        try {
            String id = generateId(table, key);
            JsonObject content = JsonObject.empty();
            HashMap<String, String> stringMap = StringByteIterator.getStringMap(values);
            for (Map.Entry<String, String> value : stringMap.entrySet()) {
                content.put(value.getKey(), value.getValue());
            }
            bucket.replace(JsonDocument.create(id, content), persistTo, replicateTo);
            return Status.OK;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error updating key: " + key);
            return Status.ERROR;
        }
    }

    @Override
    public Status insert(final String table, final String key, final HashMap<String, ByteIterator> values) {
        try {
            String id = generateId(table, key);
            JsonObject content = JsonObject.empty();
            HashMap<String, String> stringMap = StringByteIterator.getStringMap(values);
            for (Map.Entry<String, String> value : stringMap.entrySet()) {
                content.put(value.getKey(), value.getValue());
            }
            bucket.insert(JsonDocument.create(id, content), persistTo, replicateTo);
            return Status.OK;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error inserting key: " + key);
            return Status.ERROR;
        }
    }

    @Override
    public Status delete(final String table, final String key) {
        try {
            String id = generateId(table, key);
            bucket.remove(id, persistTo, replicateTo);
            return Status.OK;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error deleting key: " + key);
            return Status.ERROR;
        }
    }

    private static String generateId(String table, String key) {
        return table + "-" + key;
    }
}
