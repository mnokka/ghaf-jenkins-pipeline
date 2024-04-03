#!/usr/bin/env groovy

// SPDX-FileCopyrightText: 2024 Technology Innovation Institute (TII)
//
// SPDX-License-Identifier: Apache-2.0

////////////////////////////////////////////////////////////////////////////////

def DEF_GHAF_REPO = 'https://github.com/tiiuae/ghaf'
def DEF_GITREF = 'main'
def DEF_REBASE = false

////////////////////////////////////////////////////////////////////////////////

properties([
  // Poll every minute
  pipelineTriggers([pollSCM('* * * * *')]),
  parameters([
    string(name: 'REPO', defaultValue: DEF_GHAF_REPO, description: 'Target Ghaf repository'),
    string(name: 'GITREF', defaultValue: DEF_GITREF, description: 'Target gitref (commit/branch/tag) to build'),
    booleanParam(name: 'REBASE', defaultValue: DEF_REBASE, description: 'Rebase on top of tiiuae/ghaf main'),
  ])
])

////////////////////////////////////////////////////////////////////////////////
// TBD: fail if no analyze files


def renameAnalyzeFiles(String variantname) {

  def sourceDir = '.'
  def destinationDir = '.'

  def files = sh(script: "ls ${sourceDir}/*sbom*", returnStdout: true).trim().split('\n')

  files.each { file ->
              def fileName = file.substring(file.lastIndexOf('/') + 1)  
              def newFileName = "${variantname}_${fileName}"  
              sh "cp ${sourceDir}/${fileName} ${destinationDir}/${newFileName}"  
 }
}


////////////////////////////////////////////////////////////////////////////////

pipeline {
  agent { label 'built-in' }
  options {
    timestamps ()
    buildDiscarder logRotator(
      artifactDaysToKeepStr: '7',
      artifactNumToKeepStr: '10',
      daysToKeepStr: '70',
      numToKeepStr: '100'
    )
  }
  environment {
    // https://stackoverflow.com/questions/46680573
    REPO = params.getOrDefault('REPO', DEF_GHAF_REPO)
    GITREF = params.getOrDefault('GITREF', DEF_GITREF)
    REBASE = params.getOrDefault('REBASE', DEF_REBASE)
  }
  stages {
    // Changes to the repo/branch configured here will trigger the pipeline
    stage('Configure target repo') {
      steps {
        script {
          SCM = git(url: DEF_GHAF_REPO, branch: DEF_GITREF)
        }
      }
    }
    stage('Build') {
      stages {
        stage('Checkout') {
          steps {
            sh 'rm -rf ghaf'
            sh 'git clone $REPO ghaf'
            dir('ghaf') {
              sh 'git checkout $GITREF'
            }
          }
        }
        stage('Rebase') {
          when { expression { env.REBASE == true || params.REBASE == true } }
          steps {
            dir('ghaf') {
              sh 'git config user.email "jenkins@demo.fi"'
              sh 'git config user.name "Jenkins"'
              sh 'git remote add tiiuae https://github.com/tiiuae/ghaf.git'
              sh 'git fetch tiiuae'
              sh 'git rebase tiiuae/main'
            }
          }
        }

        // TBD: one variant operations must be separated as a single step 
        //
        stage('Build on x86_64') {
          steps {
            dir('ghaf') {
              //sh 'nix build -L .#packages.x86_64-linux.nvidia-jetson-orin-agx-debug-from-x86_64 -o result-jetson-orin-agx-debug'
              //sh 'nix build -L .#packages.x86_64-linux.nvidia-jetson-orin-nx-debug-from-x86_64  -o result-jetson-orin-nx-debug'
              sh 'nix build -L .#packages.x86_64-linux.generic-x86_64-debug                     -o result-generic-x86_64-debug'
              //sh 'nix build -L .#packages.x86_64-linux.lenovo-x1-carbon-gen11-debug             -o result-lenovo-x1-carbon-gen11-debug'
              //sh 'nix build -L .#packages.riscv64-linux.microchip-icicle-kit-debug              -o result-microchip-icicle-kit-debug'
              //sh 'nix build -L .#packages.x86_64-linux.doc                                      -o result-doc'
              // TBD: fail stage if tool fails
              //sh 'nix run github:tiiuae/sbomnix#sbomnix .#packages.x86_64-linux.generic-x86_64-debug'
              
              script {
                def sbomnixResult = sh script: 'nix run github:tiiuae/sbomnix#sbomnix -- .#packages.x86_64-linux.generic-x86_64-debug --csv result-x86_64-linux.generic-x86_64-debug.csv --cdx result-x86_64-linux.generic-x86_64-debug.cdx.json --spdx result-x86_64-linux.generic-x86_64-debug.spdx.json  ', returnStatus: true
                    if (sbomnixResult != 0) {
                        currentBuild.result = 'UNSTABLE'
                        echo "The sbomnix command failed. Setting build status to UNSTABLE."
                    } else {
                        echo "The sbomnix command succeeded. Carry on as normally"
                        //renameAnalyzeFiles("result-x86_64-linux.generic-x86_64-debug")  
                    }
              }
              
              
              
              //sh 'ls *sbom*'
              sh 'ls -h'
             // script {
             //   renameAnalyzeFiles("result-x86_64-linux.generic-x86_64-debug")     
             // }
            }
          }
        }
        stage('Build on aarch64') {
          steps {
            dir('ghaf') {
              //sh 'nix build -L .#packages.aarch64-linux.nvidia-jetson-orin-agx-debug -o result-aarch64-jetson-orin-agx-debug'
              //sh 'nix build -L .#packages.aarch64-linux.nvidia-jetson-orin-nx-debug  -o result-aarch64-jetson-orin-nx-debug'
              //sh 'nix build -L .#packages.aarch64-linux.imx8qm-mek-debug             -o result-aarch64-imx8qm-mek-debug'
              //sh 'nix build -L .#packages.aarch64-linux.doc                          -o result-aarch64-doc'
            }
          }
        }
      }
    }
  }    


  post {
	  
	    always {
        // Step to execute always, regardless of build result
        echo 'POSTBULD ALWAYS: Creating artifacts'
	    archiveArtifacts allowEmptyArchive: true, artifacts: 'ghaf/result-*/**'
		}
		
        success {
            // Step to execute when the build is successful
            echo 'POSTBUILD: Build successful'
            
            // Steps A, B, and C
            script {
                // Step A
                echo 'SUCCESS: Step A executed'
                sh 'pwd'
                sh 'ls' 
                echo 'PWD executed'
                
                // Step B
                echo 'SUCCESS: Step B executed'
                
                // Step C
                echo 'SUCCESS: Step C executed'
            }
        }
        failure {
            // Step to execute when the build fails
            echo 'POSTBUILD: Build failed'
        }    
  	}
  
  
}
