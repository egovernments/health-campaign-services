import csv
import pandas as pd
import numpy as np
import requests
import json
import openpyxl
import time
import datetime
import subprocess
import os
import string
import random
import uuid

# host and credentials for API authentication

host = 'https://health-dev.digit.org/'
username = 'GRO22'#'GRO1' # '9837872921'
password = 'eGov@4321'
tenantId='default'

# file path for reading CSV data
filePath='test_users.csv'

# URLs for API endpoints
URLS = {
    "login":host + 'employee/user/login',
    'token':host + 'user/oauth/token',
    'create_user': host + 'user/users/_createnovalidate',
    'search_user': host + 'user/_search',
    'search_project': host+"project/v1/_search?limit=10&offset=0&tenantId="+tenantId,
    'create_project_staff': host+"project/staff/v1/_create"
}

# default values for user creation
DEFAULT_VALUES = {
    "PASSWORD": password,
    "NAME": username,
    "MOBILE_NUMBER":"999999999",
    'roles': 'Registrar',
    "project_staff_start_date": 1675582245,
    "project_staff_end_date": 1707118245,
    "project_channel":"test_channel",
    "correspondence_address":"test",
    "additional_fields":{
          "schema": "registration",
          "version": 1,
          "fields": [
          ]
        }
}

# function to get access token for API requests
def accessToken():
    query = {
        'username': username,
        'password': password,
        'userType': 'EMPLOYEE',
        'scope': 'read',
        'grant_type': 'password',
        }
    query['tenantId'] = tenantId
    response = requests.post(URLS['token'], data=query,
                             headers={
        'Connection': 'keep-alive',
        'content-type': 'application/x-www-form-urlencoded',
        'origin': host,
        'Authorization': 'Basic ZWdvdi11c2VyLWNsaWVudDo=',
        })
    jsondata = response.json()
    return jsondata.get('access_token')

# function to handle API errors and update error message in dataframe
def handleError(json, df, index, errorFor = ''):
    if 'Errors' in json:
        print("Error", errorFor, df.at[index, "MOBILE_NO"])
        print(json['Errors'])
        df.at[index, 'error'] = df.at[index, 'error'] + str(json['Errors'])
        return True
    return False

# function to get user object for API request based on CSV data
def getUser(userRow):
    roles = []
    for item in userRow.get('ROLE',DEFAULT_VALUES['roles']).split(","):
        roles.append({
            "name": item.strip(),
            "code": item.strip().upper(),
            "tenantId": tenantId
        })

    if('DATE_OF_BIRTH' in userRow):
        cr_date = datetime.datetime.strptime(userRow['DATE_OF_BIRTH'], "%d/%m/%Y")
        dateOfBirth = cr_date.strftime("%d/%m/%Y")
    return  {
        "userName":userRow.get('USERNAME',uuid.uuid4()),
        "name":userRow.get('NAME',DEFAULT_VALUES.get("name")) or DEFAULT_VALUES.get("name"),
        "mobileNumber":userRow.get('MOBILE_NO',DEFAULT_VALUES.get("mobile_number")) or DEFAULT_VALUES.get("mobile_number"),
        "type": "EMPLOYEE",
        "password": userRow.get('PASSWORD', DEFAULT_VALUES.get("password")) or DEFAULT_VALUES.get("password"),
        "roles": roles,
        "active": True,
        "tenantId": tenantId,
        "permanentAddress": 'test',
        'correspondenceAddress': userRow.get('CORRESPONDENCE_ADDRESS', DEFAULT_VALUES.get("correspondence_address")) or DEFAULT_VALUES.get("correspondence_address"),
        'otpReference':'test',
        'status':'ACTIVE',
        "dob": dateOfBirth,
        "gender": "male",
        "emailId": userRow.get("EMAIL")
    }
# function to check if the user exists or not
def userExist(mobilenumber,name,requestInfo):
    # Create a dictionary with request information to be sent in API call
     userSearchRequest={}
     userSearchRequest['RequestInfo']=requestInfo
     userSearchRequest['name']=name
     userSearchRequest['mobileNumber']=mobilenumber
     userSearchRequest['tenantId']=tenantId
     userSearchRequest['userType']='EMPLOYEE'
     post_response = requests.post(url=URLS["search_user"],
                                  headers={'Content-type': 'application/json'},
                                  json=userSearchRequest)
     jsondata = post_response.json()
     if('user' in jsondata and len(jsondata.get('user'))>0):
        return jsondata.get('user')[0]['uuid']
     return None

def createUser(data,df,index):
    user = getUser(dict(data))
    query = {
        'requestInfo': {
            'authToken': accessToken()
        },
       "user": user,
    }

    # search if user already exists
    userSearchResults = userExist(user.get('mobileNumber'), user.get('name'), query.get('requestInfo'))
    # If user exists, return the UUID of the first user in the response else return node
    if userSearchResults is None:
        response = requests.post(URLS['create_user'], data=json.dumps(query),
                                 headers={
            'Content-Type': 'application/json'
            })
        jsondata = response.json()
        if(handleError(jsondata, df, index, "CREATE_USER") is False ):
            user_id = jsondata.get('user')[0].get("uuid")
            df.at[index, 'user_id'] = user_id
            df.at[index, 'user_created'] = True
            return (user_id)
    else:
        df.at[index, 'user_id'] = userSearchResults
        df.at[index, 'user_created'] = True
        return (userSearchResults)

def getProject(name):
    # Create a dictionary with request information and user details to be sent in API call
    payload = json.dumps({
      "RequestInfo": {
        "authToken": "052c8e95-35c1-4f97-b366-c5f23e23ce05"
      },
      "Projects": [
        {
          "name": name,
          "tenantId": tenantId
        }
      ]
    })
    headers = {
      'Content-Type': 'application/json'
    }
    # make api call to project service
    response = requests.request("POST", URLS["search_project"], headers=headers, data=payload)
    # Handle any errors in the API call and return JSON response
    handleError(response.json(), df, index, "GET_PROJECT")
    return response.json()

# function to create project staff
def createProjectStaff(row, df, index):
    # Create a dictionary with request information and user details to be sent in API call
    payload = json.dumps({
      "RequestInfo": {
        "authToken": accessToken(),
      },
      "ProjectStaff": {
        "tenantId": tenantId,
        "userId": df.at[index, 'user_id'],
        "projectId": df.at[index, 'project_id'],
        "startDate": DEFAULT_VALUES['project_staff_start_date'],
        "endDate": DEFAULT_VALUES['project_staff_end_date'],
        "additionalFields": DEFAULT_VALUES["additional_fields"],
        "channel": DEFAULT_VALUES["project_channel"]
      }
    })
    headers = {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    }


    response = requests.request("POST", URLS["create_project_staff"], headers=headers, data=payload)
    response_json = response.json()

    # Handle any errors in the API call and return JSON response
    if(handleError(response_json, df, index, "PROJECT_STAFF_CREATION") is False):

        df.at[index, 'project_staff_id'] = response_json.get('ProjectStaff').get("id")
        df.at[index, 'staff_created'] = True

        return response_json.get('ProjectStaff')

def readFileAndProcess():
    # Read the CSV file

    if filePath.endswith('xlsx'):
        df = pd.read_excel(filePath)
    else:
        df = pd.read_csv(filePath)

    # Convert the headers to uppercase, trim whitespace, and replace spaces with underscores
    df.columns = df.columns.str.upper().str.strip().str.replace(' ', '_')
    df['project_id'] = ''
    df['user_id'] = ''
    df['project_staff_id'] = ''
    df['error'] = ''

    # Add new columns
    df['remarks'] = ''
    df['user_created'] = ''
    df['staff_created'] = ''
    df['status'] = ''

    # Fill any missing values with empty string
    df = df.fillna('')
    return df

def start():
    # Read data from file using readFileAndProcees() function
    df = readFileAndProcess()
    # Loop over each row in the dataframe
    for index, row in df.iterrows():
        # Get the project ID from the project name
        project = getProject(row["CAMPAIGN_NAME"])
        if("Project" in project and len(project.get("Project"))>0):
            project_id = project.get("Project")[0]['id']
            df.at[index, 'project_id'] = project_id

        # Create user and project staff for the given row
        createUser(row,df,index)
        createProjectStaff(row,df,index)

    # Set the status based on whether users and project staffs were created or not
    df['status'] = np.where((df['user_created'] == True) & (df['staff_created'] == True), 'created', 'failed')
    df.to_csv(filePath) # save in same file
    print(df)

if __name__ == '__main__':
    start()

