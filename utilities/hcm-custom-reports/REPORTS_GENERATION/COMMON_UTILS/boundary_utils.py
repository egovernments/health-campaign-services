import json
import requests
import pandas as pd

boundaryUrl = "https://raw.githubusercontent.com/HCM-MOZ-IMPEL/health-campaign-mdms/PROD/data/mz/egov-location/boundary-data.json"

boundaryjson = requests.get(boundaryUrl).json()["TenantBoundary"][0]["boundary"]

def generate_linear_boundary_object(obj, parent_data=None):
    if parent_data is None:
        parent_data = {}
   
    data = parent_data.copy()
    print(obj.keys())
    data[obj['label']] = obj['name']
    data['code'] = obj['code']
    
    flattened_objects = [data]
    
    if 'children' in obj:
        for child in obj['children']:
            flattened_objects.extend(generate_linear_boundary_object(child, data))
    
    return flattened_objects

linear_boundary = generate_linear_boundary_object(boundaryjson)
code_vs_boundary_names = {}
for item in linear_boundary:
    code_vs_boundary_names[item["code"]] = [item.get("Provincia"), item.get("Distrito"), item.get("Posto Administrativo"), item.get("Localidade"), item.get("Aldeia")]
    
with open("BOUNDARY/BOUNDARY.json", "w") as file:
    json.dump(code_vs_boundary_names, file, indent=4)

df = pd.DataFrame(linear_boundary)
print(df)
df.to_excel('BOUNDARY/BOUNDARY.xlsx', index=False)

    