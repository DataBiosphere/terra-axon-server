#!/bin/bash
#
# write-config.sh extracts configuration information from vault and writes it to a set of files
# in a directory. This simplifies access to the secrets from other scripts and applications.
#
# We want to use this in a gradle task, so it takes arguments both as command line
# options and as envvars. For automations, like GHA, the command line can be specified.
# For developer use, we can set our favorite envvars and let gradle ensure the directory is properly populated.
#
# The environment passed in is used to configure several other parameters, including the target for running
# the integration tests.
#
# For personal environments, we assume that the target name is the same as the personal namespace name.
# The output directory includes the following files:
#   ---------------------------+-------------------------------------------------------------------------
#   target.txt                 | the target that generated this set of config files. Allows the script
#                              | to skip regenerating the environment on a rerun.
#   ---------------------------+-------------------------------------------------------------------------
#   testrunner-sa.json         | SA for running TestRunner - this is always taken from integration/common
#   ---------------------------+-------------------------------------------------------------------------
#   testrunner-k8s-sa-token.txt| Credentials for TestRunner to manipulate the Kubernetes cluster under
#   testrunner-k8s-sa-key.txt  | test. Not all environments have this SA configured. If the k8env is
#                              | integration and there is no configured SA, then the axonservertest one will be
#                              | retrieved. It won't work for all test runner tests.
#   ---------------------------+-------------------------------------------------------------------------
#   user-delegated-sa.json     | Firecloud SA used to masquerade as test users
#   ---------------------------+-------------------------------------------------------------------------
#   axonserver-sa.json         | SA that the terra axon server service runs as
#   ---------------------------+-------------------------------------------------------------------------

function usage {
    cat <<EOF
Usage: $0 [<target>] [<vaulttoken>] [<outputdir>] [<vaultenv>]"

  <target> can be:
    local - for testing against a local server (bootRun)
    dev - uses secrets from the dev environment
    alpha - alpha test environment
    staging - release staging environment
    help or ? - print this help
    clean - removes all files from the output directory
    * - anything else is assumed to be a personal environment using the terra-kernel-k8s
  If <target> is not specified, then use the envvar USER_WRITE_CONFIG
  If USER_WRITE_CONFIG is not specified, then use local

  <vaulttoken> defaults to the token found in ~/.vault-token.

  <outputdir> defaults to "../config/" relative to the script. When run from the gradle rootdir, it will be
  in the expected place for automation.

  <vaultenv> can be:
    docker - run a docker image containing vault
    local  - run the vault installed locally
  If <vaultenv> is not specified, then use the envvar USER_VAULT_ENV
  If USER_VAULT_ENV is not specified, then we use docker
EOF
    exit 1
}

# Get the inputs with defaulting
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." &>/dev/null && pwd)"
default_outputdir="${script_dir}/config"
default_target=${USER_WRITE_CONFIG:-local}
target=${1:-$default_target}
vaulttoken=${2:-$(cat "$HOME"/.vault-token)}
outputdir=${3:-$default_outputdir}
default_vaultenv=${USER_VAULT_ENV:-docker}
vaultenv=${4:-$default_vaultenv}

# The vault paths are irregular, so we map the target into three variables:
# k8senv    - the kubernetes environment: alpha, staging, dev, or integration
# namespace - the namespace in the k8s env: alpha, staging, dev, or the target for personal environments
# fcenv     - the firecloud delegated service account environment: dev, alpha, staging

case $target in
help | ?)
    usage
    ;;

clean)
    rm "${outputdir}"/* &>/dev/null
    exit 0
    ;;

local)
    k8senv=integration
    # TODO: probably not right, but makes the
    namespace=axonservertest
    fcenv=dev
    ;;

dev)
    k8senv=dev
    namespace=dev
    fcenv=dev
    ;;

alpha)
    k8senv=alpha
    namespace=alpha
    fcenv=alpha
    ;;

staging)
    k8senv=staging
    namespace=staging
    fcenv=staging
    ;;

\
    *) # personal env
    k8senv=integration
    namespace=$target
    fcenv=dev
    ;;
esac

# Create the output directory if it doesn't already exist
mkdir -p "${outputdir}"

# If there is a config and it matches, don't regenerate
if [ -e "${outputdir}/target.txt" ]; then
    oldtarget=$(<"${outputdir}/target.txt")
    if [ "$oldtarget" = "$target" ]; then
        echo "Config for $target already written"
        exit 0
    fi
fi

# Run vault either using a docker container or using the installed vault
function dovault {
    local dovaultpath=$1
    local dofilename=$2
    case $vaultenv in
    docker)
        docker run --rm -e VAULT_TOKEN="${vaulttoken}" broadinstitute/dsde-toolbox:consul-0.20.0 \
            vault read -format=json "${dovaultpath}" >"${dofilename}"
        ;;

    local)
        VAULT_TOKEN="${vaulttoken}" VAULT_ADDR="https://clotho.broadinstitute.org:8200" \
            vault read -format=json "${dovaultpath}" >"${dofilename}"
        ;;
    esac
}

# Read a vault path into an output file, decoding from base64
# To detect missing tokens, we need to capture the docker result before
# doing the rest of the pipeline.
function vaultgetb64 {
    vaultpath=$1
    filename=$2
    fntmpfile=$(mktemp)
    dovault "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r .data.key "${fntmpfile}" | base64 -d >"${filename}"
}

# Read a vault path into an output file
function vaultget {
    vaultpath=$1
    filename=$2
    fntmpfile=$(mktemp)
    dovault "${vaultpath}" "${fntmpfile}"
    result=$?
    if [ $result -ne 0 ]; then return $result; fi
    jq -r .data "${fntmpfile}" >"${filename}"
}

vaultget "secret/dsde/firecloud/${fcenv}/common/firecloud-account.json" "${outputdir}/user-delegated-sa.json"

## TODO: Eventually, we will need an SA
##vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/axonserver/app-sa" "${outputdir}/axonserver-sa.json"

# Test Runner SA
vaultgetb64 "secret/dsde/terra/kernel/integration/common/testrunner/testrunner-sa" "${outputdir}/testrunner-sa.json"

# Test Runner Kubernetes SA
#
# The testrunner K8s secret has a complex structure. At secret/.../testrunner-k8s-sa we have the usual base64 encoded object
# under data.key. When that is pulled out and decoded we get a structure with:
# { "data":  { "ca.crt": <base64-cert>, "token": <base64-token> } }
# The cert is left base64 encoded, because that is how it is used in the K8s API. The token is decoded.
tmpfile=$(mktemp)
vaultgetb64 "secret/dsde/terra/kernel/${k8senv}/${namespace}/testrunner-k8s-sa" "${tmpfile}"
result=$?
if [ $result -ne 0 -a "${k8senv}" = "integration" ]; then
    echo "No test runner credentials for target ${target}. Falling back to wsmtest credentials."
    vaultgetb64 "secret/dsde/terra/kernel/integration/wsmtest/testrunner-k8s-sa" "${tmpfile}"
    result=$?
fi
if [ $result -ne 0 ]; then
    echo "No test runner credentials for target ${target}."
else
    jq -r ".data[\"ca.crt\"]" "${tmpfile}" >"${outputdir}/testrunner-k8s-sa-key.txt"
    jq -r .data.token "${tmpfile}" | base64 --decode >"${outputdir}/testrunner-k8s-sa-token.txt"
fi

# We made it to the end, so record the target and avoid redos
echo "$target" >"${outputdir}/target.txt"
