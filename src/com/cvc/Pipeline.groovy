#!/usr/bin/groovy
package com.cvc;

def consul(Map config, String keyName) {
  def consulURL = config.consul[env.JOB_BASE_NAME]['url']
  def consulPrefix = config.consul.prefix
  def ssh = config.app[env.JOB_BASE_NAME]['sshJenkinsLab']

  def consulData = sh(
      script: "${ssh} curl -s http://${consulURL}/v1/kv/${consulPrefix}/${keyName} | jq '.[0].Value' | tr -d '\"'",
      returnStdout: true
  )

  def decodedData = sh(
      script: "echo '${consulData}' | base64 --decode",
      returnStdout: true
  )

  return decodedData
}

def notifyBuild(String message, String channel, String baseUrl, String tokenCredentialId, String buildStatus = 'STARTED') {
    buildStatus = buildStatus ?: 'SUCCESSFUL'
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.JOB_URL})"

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL' || buildStatus == 'SUCCESS') {
        colorCode = '#00FF00'
    } else {
        colorCode = '#FF0000'
    }

    slackSend(channel: channel,
              baseUrl: baseUrl,
              tokenCredentialId: tokenCredentialId,
              color:"${colorCode}",
              message: "${summary} ${message}")
}

def getContainerTags(Map git, Map tags = [:]) {
    println "getting list of tags for container"
    def String commit_tag
    def String version_tag

    // commit tag
    try {
        // if branch available, use as prefix, otherwise only commit hash
        if (env.BRANCH_NAME) {
            commit_tag = env.BRANCH_NAME + '-' + git.GIT_COMMIT.substring(0, 8)
        } else {
            commit_tag = git.GIT_COMMIT.substring(0, 8)
        }
        tags << ['commit': commit_tag]
    } catch (Exception e) {
        println "WARNING: commit unavailable from env. ${e}"
    }

    // build tag only if none of the above are available
    if (!tags) {
        try {
            tags << ['build': env.BUILD_TAG]
        } catch (Exception e) {
            println "WARNING: build tag unavailable from config.project. ${e}"
        }
    }

    return getMapValues(tags)
}

@NonCPS
def getMapValues(Map map=[:]) {
    // jenkins and workflow restriction force this function instead of map.values(): https://issues.jenkins-ci.org/browse/JENKINS-27421
    def entries = []
    def map_values = []

    entries.addAll(map.entrySet())

    for (int i=0; i < entries.size(); i++){
        String value =  entries.get(i).value
        map_values.add(value)
    }

    return map_values
}

@NonCPS
def generateFile(Map config, String sourcePath, String destPath, String dockerImage="") {
  def binding = [
     app_name       : config.app.name,
     app_replicas   : config.app.replicas,
     container_port : config.app.port,
     docker_image   : dockerImage
  ]

  def engine = new groovy.text.SimpleTemplateEngine()

  def template = readFile(sourcePath)
  def deploymentFileText = engine.createTemplate(text).make(binding)

  def deploymentFile = new File(destPath)
  file.newWriter().withWriter { w ->
    w << deploymentFileText.toString()
  }
}

def createNamespace(String kubectl, String namespaceName) {
  def namespace = sh(
      script: "${kubectl} get namespace ${namespaceName}",
      returnStatus: true
  )

  if (namespace != 0) {
    sh("${kubectl} create namespace ${namespaceName}")
  }
}

def deploy(Map args) {
  def namespace = ""

  if (args.namespace == null) {
    namespace = "default"
  } else {
    createNamespace(args.kubectl, args.namespace)
    namespace = args.namespace
  }

  def kubectl = "${args.kubectl} --namespace ${namespace}"
  def deployment = sh(
      script: "${kubectl} get deployment ${args.deployName}",
      returnStatus: true
  )

  if (deployment != 0) {
    sh("${kubectl} create -f ${args.deploymentFile}")
  } else {
    sh("${kubectl} set image deployment/${args.deployName} ${args.containerName}=${args.dockerImage}")
  }

  sh "${kubectl} apply -f ${args.serviceFile}"
}

def cleanDeploy() {
  def dockerImageId = sh(
      script: "docker images | awk '{print \$3}' | sed '2!d'",
      returnStdout: true
  )

  if (dockerImageId != "") {
    sh("docker rmi -f ${dockerImageId}")
  }
}

def requestToHealthCheck(Map args){
  def httpResponse = sh(
    script: "${args.ssh} curl -s ${args.serviceEndpoint}",
    returnStdout: true
  )

  return httpResponse.equals("OK")
}
