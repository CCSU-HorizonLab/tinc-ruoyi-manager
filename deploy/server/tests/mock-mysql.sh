#!/usr/bin/env bash
set -euo pipefail

statement=
for argument in "$@"; do
    case $argument in --execute=*) statement=${argument#--execute=} ;; esac
done
[[ -n $statement ]] || exit 2

case $statement in
    'SELECT DATABASE()')
        [[ ${MOCK_SCHEMA_STATE:-valid} == connection_error ]] && exit 1
        echo "${MOCK_DATABASE:-ry-tinc}" ;;
    *'information_schema.TABLES'*)
        if [[ ${MOCK_SCHEMA_STATE:-valid} == missing ]]; then :
        elif [[ ${MOCK_SCHEMA_STATE:-valid} == wrong_engine ]]; then echo MyISAM
        else echo InnoDB; fi ;;
    *'information_schema.COLUMNS'*)
        if [[ ${MOCK_SCHEMA_STATE:-valid} == invalid_columns ]]; then echo 4; else echo 5; fi ;;
    *"INDEX_NAME = 'PRIMARY'"*)
        echo 'user_id,resource_type,resource_id' ;;
    *"INDEX_NAME = 'idx_tinc_grant_resource'"*)
        if [[ ${MOCK_SCHEMA_STATE:-valid} == invalid_index ]]; then echo 'resource_id,resource_type'; else echo 'resource_type,resource_id'; fi ;;
    *'FROM tinc_resource_grant WHERE 1 = 0'*)
        if [[ ${MOCK_SCHEMA_STATE:-valid} == broken_probe ]]; then exit 1; else echo 0; fi ;;
    *) exit 2 ;;
esac
