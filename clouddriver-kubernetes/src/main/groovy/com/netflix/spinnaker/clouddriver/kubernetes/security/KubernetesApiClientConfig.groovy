/*
 * Copyright 2017 Cisco, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;

import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import java.nio.file.Files;

@Slf4j
public class KubernetesApiClientConfig extends Config {
  String configKey
  String context
  String cluster
  String user
  String userAgent
  Boolean serviceAccount

  public KubernetesApiClientConfig(String configKey, String context, String cluster, String user, String userAgent, Boolean serviceAccount) {
    this.configKey = configKey
    this.context = context
    this.user = user
    this.userAgent = userAgent
    this.serviceAccount = serviceAccount
  }

  public ApiClient getApiCient() throws Exception {
    if (serviceAccount) {
      return withServiceAccount()
    } else {
      return withKubeConfig()
    }
  }

  ApiClient withServiceAccount() {
    ApiClient client = new ApiClient()

    try {
      boolean serviceAccountCaCertExists = Files.isRegularFile(new File(io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).toPath())
      if (serviceAccountCaCertExists) {
        client.setSslCaCert(new FileInputStream(io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH))
      } else {
        throw new IllegalStateException("Could not find CA cert for service account at $io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH")
      }
    } catch(IOException e) {
      throw new IllegalStateException("Could not find CA cert for service account at $io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH", e)
    }

    try {
      String serviceTokenCandidate = new String(Files.readAllBytes(new File(io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).toPath()))
      if (serviceTokenCandidate != null) {
        client.setApiKey("Bearer " + serviceTokenCandidate)
      } else {
        throw new IllegalStateException("Did not find service account token at $io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH")
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read service account token at $io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH", e)
    }

    return client
  }

  ApiClient withKubeConfig() {
    KubeConfig kubeconfig

    try {
      kubeconfig = parseConfig(configKey)
    } catch (IOException e) {
      throw new IllegalStateException("Parse config" + configKey + "failed");
    }

    //TODO: Need to validate cluster and user when client library exposes these api.
    if (StringUtils.isEmpty(context) && !kubeconfig.currentContext) {
      throw new RuntimeException("Missing required field ${context} in kubeconfig file and clouddriver configuration.")
    }

    if (!StringUtils.isEmpty(context)) {
      kubeconfig.setContext(context)
    }

    ApiClient client = Config.fromConfig(kubeconfig)

    if (!StringUtils.isEmpty(userAgent)) {
      client.setUserAgent(userAgent)
    }

    is.close()
    input.close()

    return client
  }

  private static KubeConfig parseConfig(String config) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
    return mapper.readValue(config, KubeConfig.class)
  }

}
