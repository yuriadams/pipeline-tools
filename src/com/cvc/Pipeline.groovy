#!/usr/bin/groovy
package com.cvc;

def kubectlTest() {
  // Test that kubectl can correctly communication with the Kubernetes API
  println "checking kubectl connnectivity to the API"
  sh "kubectl get nodes"
}
