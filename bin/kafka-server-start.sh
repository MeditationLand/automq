#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

calculate_max_heap_kb() {
    if [[ "$(uname)" == "Linux" ]]; then
        local total_mem_kb
        local mem_75_percent_kb
        local mem_32_gb_in_kb
        total_mem_kb="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
        mem_75_percent_kb="$((total_mem_kb * 3 / 4))"
        mem_32_gb_in_kb="$((32 * 1024 * 1024))"
        # Return the smaller of 75% of the total memory or 32GB
        echo "$((mem_75_percent_kb < mem_32_gb_in_kb ? mem_75_percent_kb : mem_32_gb_in_kb))"
    else
        # Use 1GB as the default heap size on other platforms for development purposes
        echo "$((1 * 1024 * 1024))"
    fi
}

if [ $# -lt 1 ];
then
	echo "USAGE: $0 [-daemon] server.properties [--override property=value]*"
	exit 1
fi
base_dir=$(dirname $0)

if [ "x$KAFKA_LOG4J_OPTS" = "x" ]; then
    export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:$base_dir/../config/log4j.properties"
fi

if [ "x$KAFKA_HEAP_OPTS" = "x" ]; then
    max_heap_kb=$(calculate_max_heap_kb)
    export KAFKA_HEAP_OPTS="-Xmx${max_heap_kb}k -Xms${max_heap_kb}k -XX:MetaspaceSize=96m"
fi

if [ "x$KAFKA_OPTS" = "x" ]; then
    export KAFKA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -Dio.netty.allocator.maxOrder=11"
fi

EXTRA_ARGS=${EXTRA_ARGS-'-name kafkaServer -loggc'}

COMMAND=$1
case $COMMAND in
  -daemon)
    EXTRA_ARGS="-daemon "$EXTRA_ARGS
    shift
    ;;
  *)
    ;;
esac

exec $base_dir/kafka-run-class.sh $EXTRA_ARGS kafka.Kafka "$@"
