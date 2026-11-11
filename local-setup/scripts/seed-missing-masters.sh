#!/bin/bash
# Master data the web UI needs and db/full-dump.sql does not have. Safe to run again.
#
#   HCM-ADMIN-CONSOLE.productType   the "Resource type" dropdown on the console's "Add new resource" screen.
#       Values are the ones in DIGIT-Frontend:
#       health/micro-ui-react19/web/packages/modules/campaign-manager/src/configs/productType.js
#       (that file says "migrated to mdms", but the dump has no such master).
set -uo pipefail
# Talks to the MDMS service directly (not through the gateway on 28000, which needs a login token).
MDMS=${MDMS:-http://localhost:28094}
TENANT=${TENANT:-mz}
RI='{"apiId":"local-setup","msgId":"seed","userInfo":{"id":1,"uuid":"dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3","userName":"SYSTEM","tenantId":"'$TENANT'","type":"EMPLOYEE","roles":[{"code":"SUPERUSER","tenantId":"'$TENANT'"},{"code":"MDMS_ADMIN","tenantId":"'$TENANT'"}]}}'
post() { curl -s -o /dev/null -w "  %{http_code}  $1\n" -X POST "$MDMS$1" -H 'Content-Type: application/json' --data "$2"; }

echo "schema HCM-ADMIN-CONSOLE.productType (400 means it already exists)"
post /mdms-v2/schema/v1/_create '{"RequestInfo":'"$RI"',"SchemaDefinition":{"tenantId":"'$TENANT'","code":"HCM-ADMIN-CONSOLE.productType","description":"Product types offered when adding a resource","isActive":true,"definition":{"type":"object","$schema":"http://json-schema.org/draft-07/schema#","required":["key","code"],"x-unique":["code"],"properties":{"key":{"type":"number"},"code":{"type":"string"}}}}}'

echo "data (400 means it already exists)"
i=0; for code in BEDNET TABLET DRUG; do i=$((i+1))
  post /mdms-v2/v2/_create/HCM-ADMIN-CONSOLE.productType '{"RequestInfo":'"$RI"',"Mdms":{"tenantId":"'$TENANT'","schemaCode":"HCM-ADMIN-CONSOLE.productType","data":{"key":'$i',"code":"'$code'"},"isActive":true}}'
done
