# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
FROM azul/zulu-openjdk-debian:17.0.2-jre-headless as runner

RUN export DEBIAN_FRONTEND=noninteractive && apt-get update \
	&& apt-get install -y --no-install-recommends locales netcat wget unzip tzdata telnet vim dos2unix curl software-properties-common gnupg apt-transport-https software-properties-common \
	&& ln -fs /usr/share/zoneinfo/Asia/Aden /etc/localtime \
	&& dpkg-reconfigure --frontend noninteractive tzdata dos2unix \
        && sed -i -e 's/# es_MX.UTF-8 UTF-8/es_MX.UTF-8 UTF-8/' /etc/locale.gen \
        && locale-gen  \
	&& apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

ENV LANG es_MX.UTF-8  
ENV LANGUAGE es_MX:es  
ENV LC_ALL es_MX.UTF-8   

COPY entrypoint.sh .

RUN chmod +x /entrypoint.sh

WORKDIR /tmp

COPY ./target/phee-service-entrypoint-0.0.1.jar phee-service-entrypoint-0.0.1.jar

EXPOSE 61616

ENTRYPOINT /entrypoint.sh
