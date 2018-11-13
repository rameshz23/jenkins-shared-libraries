import hudson.model.Result
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper


def cicdProjects = [

   [application_platform: 'Rcopia',application_name:'piserviceupdater201',scm: 'git@git.drfirst.com:devops-se/chef-dev-rcopia.git',dev:'jsingh',qa:'jsingh',sysOps:'jsingh',
  ,label:'slave1']
 
]


for (project in cicdProjects) {
def application_platform = project['application_platform']
def application_name = project['application_name']
def projRepo = project['scm']
def dev = project['dev']
def qa = project['qa']
def sysOps = project['sysOps']
def agentLabel = project['label']
def gitCred='svc-devops'
def svnCred='svn'
def slackGroup=project['slack_group']
def opsSlackGroup='jenkins-test'

job("${application_platform}_${application_name}_Dev-Deploy") {
  steps {
      logRotator { numToKeep 5 }
     label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"  }
    shell{
command('''cat data_bags/app_'''+"""$application_name"""+'''/dev.json | jq .server_list | grep ":" | head -1 > name.txt
sed -e 's/{//g'  -e 's/://g'  -e 's/"//g' -e 's/ //g' name.txt > tmp.txt

echo "test" >envVars.properties
echo "need to create the build number"
grep "version" data_bags/app_'''+"""${application_name}"""+'''/dev.json | head -1 > tmp
version=$(cat tmp | awk -F':' '{print $2}')
version="${version//,/}"
version="${version//\\"/}"

build_commit_hash_number=$(cat data_bags/app_'''+"""$application_name"""+'''/dev.json | grep build | head -1 | awk -F: '{ print $2 }' | sed 's/[\",]//g' | tr -d '[[:space:]]')
echo "build_commit_hash_number:$build_commit_hash_number"

echo "BUILD_NUMBER='''+"""$application_name"""+'''_${version}_${build_commit_hash_number}_${BUILD_ID}" >envVars.properties
grep "role" data_bags/app_'''+"""${application_name}"""+'''/dev.json | head -1 > role
role=$(cat role | awk -F':' '{print $2}')
  role="${role//,/}"
  role="${role//\\"/}"
  role="${role// /}"
rm -rf role
echo "sudo chef-client " > runlist.txt
knife ssh name:`cat tmp.txt` -x appuser -i '~/.chef/appuser.pem' -a ipaddress "`cat runlist.txt`"'''
              )
}

     environmentVariables {
        propertiesFile('envVars.properties')
    }


    scm{ git { remote{
          url("${projRepo}")
            credentials("$gitCred")
            branches('*/master')  }  } }
   downstreamParameterized  {
          trigger("${application_platform}_${application_name}_Dev-Promotion") {
        parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}
        }
  }
   wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
        timestamps()
      preBuildCleanup{
       cleanupParameter()}
    }

    publishers {
        wsCleanup()
    }
     }

       job("${application_platform}_${application_name}_Dev-Promotion") {
  steps {
      logRotator { numToKeep 5 }
     label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"
     }
    }
   wrappers {
        buildName('${BUILD_NUMBER}')
       colorizeOutput()
        timestamps()
    }
  properties{
        promotions{
            promotion {
                name("DevtoQA")
                icon('star-gold')
                conditions {
                    manual("$dev")
                }
              actions {
                   downstreamParameterized  {
          trigger("${application_platform}_${application_name}_QA-Upload") {
          parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}    }  }   }   }}

    publishers {
        wsCleanup()
    }
}

job("${application_platform}_${application_name}_QA-Upload") {
    steps {
      logRotator { numToKeep 5 }
       label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"  }

       scm{ git { remote{
          url("${projRepo}")
            credentials("$gitCred")
            branches('*/master')  }  } }

}
     wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
       preBuildCleanup{
       cleanupParameter()}

   }

  steps {
    shell{
command('''version=`echo ${BUILD_NUMBER}| rev | cut -d_ -f3 | rev`
version="\\\"version\\\":\\\"`echo $version`\\\","
build=`echo ${BUILD_NUMBER}| rev | cut -d_ -f2 | rev`
build="\\\"build\\\":\\\"`echo $build`\\\","
git checkout master
sed -i "1,/\\\"version.*/s/\\\"version.*/`echo $version`/g" ''' + """ "data_bags/app_$application_name/qa.json"
""" +'''
sed -i "1,/\\\"build.*/s/\\\"build.*/`echo $build`/g" ''' + """ "data_bags/app_$application_name/qa.json"
""" +'''
git add .
if [ -n "$(git status --porcelain)" ]; then
git commit -m "promoting the version from dev to qa"
git push origin master
knife data bag  from file ''' + """ app_$application_name  data_bags/app_$application_name/qa.json
""" +
'''else
echo 'no changes';
fi
'''
)

    }


        downstreamParameterized  {
          trigger("${application_platform}_${application_name}_QA-Deploy") {

        parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}

        }
}

  publishers {
        wsCleanup()
    }

}

job("${application_platform}_${application_name}_QA-Deploy") {
  steps {
      logRotator { numToKeep 5 }
    label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"  }
      shell{
      command('''cat data_bags/app_'''+"""$application_name"""+'''/qa.json | jq .server_list | grep ":" | head -1 > name.txt
sed -e 's/{//g'  -e 's/://g'  -e 's/"//g' -e 's/ //g' name.txt > tmp.txt

echo "test" >envVars.properties
grep "role" data_bags/app_'''+"""${application_name}"""+'''/qa.json | head -1 > role
role=$(cat role | awk -F':' '{print $2}')
  role="${role//,/}"
  role="${role//\\"/}"
  role="${role// /}"
rm -rf role
echo "sudo chef-client" > runlist.txt
knife ssh name:`cat tmp.txt` -x appuser -i '~/.chef/appuser.pem' -a ipaddress "`cat runlist.txt`"'''
              )
}


    scm{ git { remote{
          url("${projRepo}")
            credentials("$gitCred")
            branches('*/master')  }  } }


    wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
         preBuildCleanup{
       cleanupParameter()}
    }

       downstreamParameterized  {
          trigger("${application_platform}_${application_name}_QA-SignOff") {
        parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}
        }
      }
  publishers {
        wsCleanup()
    }

}

job("${application_platform}_${application_name}_QA-Signoff") {
  steps {
      logRotator { numToKeep 5 }
    label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"
      }
          steps { scm{ git { remote{
          url('git@git.drfirst.com:devops-se/chef-sysops.git')
            credentials('svc-devops')
            branches('*/master')  }  } }
    }
    }
   wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
        preBuildCleanup{
       cleanupParameter()}
    }
  properties{
        promotions{
            promotion {
              name("${JOB_NAME}")
                icon('star-gold')
                conditions {
                    manual("$qa")
                }
              actions {
                   downstreamParameterized  {
          trigger("${application_platform}_${application_name}_Staging-Promotion") {
          parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}    }  }   }   }}
 /** steps {
    shell{
command(''' echo "BUILD NUMBER: ${BUILD_NUMBER}"

version=`echo ${BUILD_NUMBER}| rev | cut -d_ -f3 | rev | sed 's/ //g' `
staging_version=$(cat ''' + """ "data_bags/app_$application_name/staging.json" """ +''' | grep version | head -1 | awk -F: '{ print $2 }' | sed 's/[\",]//g' | tr -d '[[:space:]]')
echo "staging_version:$staging_version"

function version_gt() { test "$(printf '%s\n' "$@" | sort -V | head -n 1)" != "$1"; }

if version_gt $version $staging_version; then
  echo "$version is greater than $staging_version !"
else
  echo "You are trying to promote a version that is lesser than Staging Version"
  exit 1
fi
'''
)

    }
  } */
   publishers {
        wsCleanup()
    }
}

job("${application_platform}_${application_name}_Staging-Promotion") {
  steps {
      logRotator { numToKeep 5 }
       label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"
      }
    }
   wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
        timestamps()
    }
  properties{
        promotions{
            promotion {
              name("${JOB_NAME}")
                icon('star-gold')
                conditions {
                    manual("$sysOps")
                }
              actions {
                   downstreamParameterized  {
          trigger("${application_platform}_${application_name}_Staging-Upload") {
          parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}    }  }   }   }}
    publishers {
        wsCleanup()
    }
}



job("${application_platform}_${application_name}_Staging-Upload") {
    steps {
      logRotator { numToKeep 5 }
         label("${agentLabel}")
  parameters {

    stringParam "BUILD_NUMBER"  }

      steps { scm{ git { remote{
          url('git@git.drfirst.com:devops-se/chef-sysops.git')
            credentials('svc-devops')
            branches('*/master')  }  } }
    }
}
     wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
         preBuildCleanup{
        cleanupParameter()}
   }

  steps {
    shell{
command(''' version=`echo ${BUILD_NUMBER}| rev | cut -d_ -f3 | rev`
version="\\\"version\\\":\\\"`echo $version`\\\","
build=`echo ${BUILD_NUMBER}| rev | cut -d_ -f2 | rev`
build="\\\"build\\\":\\\"`echo $build`\\\""
git checkout master
sed -i "1,/\\\"version.*/s/\\\"version.*/`echo $version`/g" ''' + """ "data_bags/app_$application_name/staging.json"
""" +'''
sed -i "1,/\\\"build.*/s/\\\"build.*/`echo $build`/g" ''' + """ "data_bags/app_$application_name/staging.json"
""" +'''
git add .
if [ -n "$(git status --porcelain)" ]; then
git commit -m "promoting the version from qa to staging"
git push origin master
knife data bag  from file''' + """ app_$application_name  data_bags/app_$application_name/staging.json
""" +
'''else
echo 'no changes';
fi

'''
)

    }


        downstreamParameterized  {
          trigger("${application_platform}_${application_name}_Prod-Promotion") {

        parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}

        }
      }
   publishers {
        wsCleanup()
    }
}


job("${application_platform}_${application_name}_Prod-Promotion") {
  steps {
      logRotator { numToKeep 5 }
       label("${agentLabel}")
  parameters {
    stringParam "BUILD_NUMBER"
      }
    }
   wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
        timestamps()
        preBuildCleanup{
       cleanupParameter()}
    }
  properties{
        promotions{
            promotion {
              name("${JOB_NAME}")
                icon('star-gold')
                conditions {
                    manual("$sysOps")
                }
              actions {
                   downstreamParameterized  {
          trigger("${application_platform}_${application_name}_Prod-Upload") {
          parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}    }  }   }   }}

   publishers {
        wsCleanup()
    }
}

job("${application_platform}_${application_name}_Prod-Upload") {
    steps {
      logRotator { numToKeep 5 }
       label("${agentLabel}")
  parameters {

    stringParam "BUILD_NUMBER"  }

      steps { scm{ git { remote{
          url('git@git.drfirst.com:devops-se/chef-sysops.git')
            credentials('svc-devops')
            branches('*/master')  }  } }
    }
}
     wrappers {
        buildName('${BUILD_NUMBER}')
        colorizeOutput()
        timestamps()
        preBuildCleanup{
       cleanupParameter()}
   }

  steps {
    shell{
command(''' version=`echo ${BUILD_NUMBER}| rev | cut -d_ -f3 | rev`
version="\\\"version\\\":\\\"`echo $version`\\\","

build=`echo ${BUILD_NUMBER}| rev | cut -d_ -f2 | rev`
build="\\\"build\\\":\\\"`echo $build`\\\""

git checkout master
sed -i "1,/\\\"version.*/s/\\\"version.*/`echo $version`/g" ''' + """ "data_bags/app_$application_name/prod.json"
""" +
'''
sed -i "1,/\\\"build.*/s/\\\"build.*/`echo $build`/g" ''' + """ "data_bags/app_$application_name/prod.json"
""" +
'''
git add .
if [ -n "$(git status --porcelain)" ]; then
git commit -m "promoting the version from staging to prod"
 git push origin master
knife data bag  from file ''' + """ app_$application_name  data_bags/app_$application_name/prod.json
""" +
'''else
echo 'no changes';
fi
'''
)


    }

    downstreamParameterized  {
          trigger("${application_platform}_${application_name}_QA_Prod-Deploy") {

        parameters{
          predefinedProps([BUILD_NUMBER: '$BUILD_NUMBER'])
                  }}
        }
  }
   publishers {
        wsCleanup()
    }
}

job("${application_platform}_${application_name}_QA_Prod-Deploy") {
  steps {
      logRotator { numToKeep 5 }
       label("${agentLabel}")
  parameters {
    stringParam "DEV_EMAIL"
    stringParam "BUILD_NUMBER"  }
  steps {  scm{ git { remote{
          url("${projRepo}")
            credentials("$gitCred")
            branches('*/master')  }  } }
          }
    }
    wrappers {
         buildName('${BUILD_NUMBER}')
         colorizeOutput()
         timestamps()
         preBuildCleanup{
        cleanupParameter()}
      }
     steps {
    shell{
              command(''' version=`echo ${BUILD_NUMBER}| rev | cut -d_ -f2 | rev | sed 's/ //g'`
    version="\\\"version\\\":\\\"`echo $version`\\\","
    git checkout master
    sed -i "1,/\\\"version.*/s/\\\"version.*/`echo $version`/g" ''' + """ "data_bags/app_$application_name/qa_prod.json"
    """ +'''
    git add .
    if [ -n "$(git status --porcelain)" ]; then
    git commit -m "syncing the version from prod to qa_prod"
    git push origin master
    knife data bag  from file ''' + """ app_$application_name  data_bags/app_$application_name/qa_prod.json
    """ +
    '''else
    echo 'no changes';
    fi

    cat data_bags/app_'''+"""$application_name"""+'''/qa_prod.json | jq .server_list | grep ":" | head -1 > name.txt
    sed -e 's/{//g'  -e 's/://g'  -e 's/"//g' -e 's/ //g' name.txt > tmp.txt

    echo "sudo chef-client" > runlist.txt
    knife ssh name:`cat tmp.txt` -x appuser -i '~/.chef/appuser.pem' -a ipaddress "`cat runlist.txt`"'''
              )
        }
     }
        publishers {
        wsCleanup()
    }
   }

deliveryPipelineView("${application_platform}_${application_name}") {

    pipelineInstances(1)
    showAggregatedPipeline(true)
    columns(1)
    sorting(Sorting.TITLE)
    allowRebuild(true)
    updateInterval(2)
    enableManualTriggers()
    showAvatars()
    showChangeLog(true)
    pipelines {
         component('DEV & QA', "${application_platform}_${application_name}_Dev-Deploy")
        component('Staging & Prod',"${application_platform}_${application_name}_Staging-Promotion")
        }
  configure { view ->
    def components = view / componentSpecs
    components.'se.diabol.jenkins.pipeline.DeliveryPipelineView_-ComponentSpec'[0] << lastJob("${application_platform}_${application_name}_QA-Signoff")
    components.'se.diabol.jenkins.pipeline.DeliveryPipelineView_-ComponentSpec'[1] << lastJob("${application_platform}_${application_name}_QA_Prod-Deploy")
  }
}
}
