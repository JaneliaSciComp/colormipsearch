#!/bin/bash

SCRIPT_DIR="$(dirname "$0")"

CDSPARAMS=${CDSPARAMS:-${SCRIPT_DIR}/cdsparams.sh}
source ${SCRIPT_DIR}/cdsparams.sh
source ${CDSPARAMS}

export CDGA_INPUT_DIR=${CDSMATCHES_RESULTS_DIR}/${RESULTS_SUBDIR_FOR_LIBRARIES}
export CDGA_OUTPUT_DIR=${CDGAS_RESULTS_DIR}/${RESULTS_SUBDIR_FOR_LIBRARIES}

export TOTAL_JOBS=$(((TOTAL_FILES - START_FILE_INDEX) / FILES_PER_JOB))

function localRun {
    if [[ $# -lt 2 ]] ; then
      echo "localRun <from> <to>"
            exit 1
    fi
    from=$1
    to=$2
    echo "Running jobs: ${from} - ${to}"
    for ((LSB_JOBINDEX=${from}; LSB_JOBINDEX<=${to}; LSB_JOBINDEX++)) ; do
        ${SCRIPT_DIR}/submitUpdateRevGAJob.sh ${CDGA_INPUT_DIR} ${CDGA_OUTPUT_DIR} ${LSB_JOBINDEX}
    done
}

function gridRun {
    if [[ $# -lt 2 ]] ; then
      echo "gridRun <from> <to>"
            exit 1
    fi
    from=$1
    to=$2
    echo "Running jobs: ${from} - ${to}"
    bsub -n ${CORES_RESOURCE} -J CDGA[${from}-${to}] -P emlm \
        ${SCRIPT_DIR}/submitUpdateRevGAJob.sh ${CDGA_INPUT_DIR} ${CDGA_OUTPUT_DIR}
}

echo "Total jobs: ${TOTAL_JOBS}"

# to run locally use localRun <from> <to>
# to run on the grid use gridRun <from> <to>
FIRST_JOB=${FIRST_JOB:-1}
LAST_JOB=${LAST_JOB:-${TOTAL_JOBS}}
startcmd="${RUN_CMD} ${FIRST_JOB} ${LAST_JOB}"
echo $startcmd
($startcmd)
