#!/bin/bash

# Download an artifact from the nexus repository
downloadArtifact() {
  if [[ $# -ne 5 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [nexus_username] [nexus_password] [nexus_url] [download location] [artifact_name]";
    echo "Example: $0 my_nexus_username my_nexus_password \ "
    echo "        https://devtools.jahia.com/nexus/service/local/repositories/jahia-enterprise-releases/content/org/jahia/keymaker/keymaker-cli/2.0/keymaker-cli-2.0.jar \ "
    echo "        target/keymaker keymaker-cli";
    echo "***************************************************"
    exit 1;
  fi
  nexus_username=$1;
  nexus_password=$2;
  nexus_url=$3;
  download_location=$4;
  artifact_name=$5;
  mkdir -p ${download_location}
  curl  --header "Authorization: Basic $(echo -n ${nexus_username}:${nexus_password} | base64)"\
        --url "${nexus_url}" \
        --output "${download_location}/${artifact_name}.jar"
}

mavenEvaluate() {
  if [[ $# -ne 2 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file] [expression] ";
    echo "Example: $0 ~/source 'project.groupId'"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  expression=$2;
  echo "$(mvn -f ${pom_location} help:evaluate -Dexpression=${expression} -q -DforceStdout | xargs)"
}

# Gets a list of submodules
getSubmoduleProjects() {
  if [[ $# -ne 1 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file]";
    echo "Example: $0 ~/source"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  echo "$(mvn -f ${pom_location} -q --also-make exec:exec -Dexec.executable="pwd")"
}
# Get the group id from the pom.xml
getGroupId() {
  if [[ $# -ne 1 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file]";
    echo "Example: $0 ~/source"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  echo "$(mavenEvaluate ${pom_location} 'project.groupId')";
}

# Get the group id of the parent from the pom.xml
getParentGroupId() {
  if [[ $# -ne 1 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file]";
    echo "Example: $0 ~/source"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  echo "$(mavenEvaluate ${pom_location} 'project.parent.groupId')";
}

# Get project name from the pom.xml
getProjectName() {
  if [[ $# -ne 1 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file]";
    echo "Example: $0 ~/source"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  echo "$(mavenEvaluate ${pom_location} 'project.name')"
}

# Get the packaging type from the pom.xml
# E.g: bundle or pom
getPackingType() {
  if [[ $# -ne 1 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [location of pom file]";
    echo "Example: $0 ~/source"
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  echo "$(mavenEvaluate ${pom_location} 'project.packaging')"
}

# Update the signature in the pom.xml file and push
updateSignature() {
  if [[ $# -lt 2 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [pom location] [signature]";
    echo "Example: $0 ~/source new-signature";
    echo "***************************************************"
    exit 1;
  fi
  pom_location=$1;
  new_signature=$2;
  if [[ $(uname -s) == "Darwin" ]]; then
    sed -i '' "s%<\(.*\)Jahia-Signature>.*</%<\1Jahia-Signature>${new_signature}</%" "${pom_location}/pom.xml";
    sed -i '' "s%<\(.*\)jahia-module-signature>.*</%<\1jahia-module-signature>${new_signature}</%" "${pom_location}/pom.xml";
  else
    sed -i "s%<\(.*\)Jahia-Signature>.*</%<\1Jahia-Signature>${new_signature}</%" "${pom_location}/pom.xml";
    sed -i "s%<\(.*\)jahia-module-signature>.*</%<\1jahia-module-signature>${new_signature}</%" "${pom_location}/pom.xml";
  fi
}

# Checks if the signature needs to be updated
updateSignatureInPom() {
  if [[ $# -ne 2 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [keymaker-cli path] [pom location]";
    echo "Example: $0 target/keymaker/keymaker-cli.jar ~/source";
    echo "***************************************************"
    exit 1;
  fi
  keymaker_cli_jar=$1;
  pom_location=$2;
  group_id=$(getGroupId "${pom_location}")
  parent_group_id=$(getParentGroupId "${pom_location}")
  if [[ ${group_id} == "org.jahia.modules" || ${parent_group_id} == "org.jahia.modules" ]]; then
    new_signature=$(java -jar "${keymaker_cli_jar}" pom -f "${pom_location}/pom.xml" -s)
    if [[ $(echo "${new_signature}" | grep -c "Signature valid") != 1 ]]; then
      updateSignature "${pom_location}" "${new_signature}"
    fi
  fi
}

handleSignatureUpdate() {
  if [[ $# -ne 2 ]]; then
    echo "***************************************************"
    echo "USAGE: $0 [pom location] [keymaker-cli path]";
    echo "Example: $0 ~/source target/keymaker/keymaker-cli.jar";
    echo "***************************************************"
    exit 1;
  fi
  root_dir=$1
  keymaker_cli_jar=$2;
  packaging_type=$(getPackingType "${root_dir}")
  case ${packaging_type} in
  pom)
    list_of_subprojects=($(getSubmoduleProjects "${root_dir}"));
    for submodule in "${list_of_subprojects[@]:1}"; do
      if [[ "${submodule}" != "${root_dir}" ]]; then
        submodule_packaging_type=$(getPackingType "${submodule}")
        if [[ ${submodule_packaging_type} == "bundle" ]]; then
          updateSignatureInPom "${keymaker_cli_jar}" "${submodule}"
        fi
      fi
    done
    ;;
  bundle)
    updateSignatureInPom "${keymaker_cli_jar}" "${root_dir}"
    ;;
  esac
}
