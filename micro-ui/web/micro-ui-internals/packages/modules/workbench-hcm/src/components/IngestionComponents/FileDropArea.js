import React, { useState, useRef, useEffect } from 'react';
import { ActionBar, Header, Loader, SubmitBar, Toast} from "@egovernments/digit-ui-react-components";

function FileDropArea ({ingestionType}) {
  const [isDragActive, setIsDragActive] = useState(false);
  const [droppedFile, setDroppedFile] = useState(null);
  const [showToast, setShowToast] = useState(false);
  const [response, setResponse] = useState(null);
  const [isResponseLoading, setIsResponseLoading] = useState(false);
  const [isLoading, setIsLoading] = useState(false);


  const fileInputRef = useRef(null);

  const handleDragEnter = (e) => {
    e.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = () => {
    setIsDragActive(false);
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  const handleFileInput = (e) => {
    e.preventDefault();
  setIsDragActive(false);

  const files = e.dataTransfer ? e.dataTransfer.files : e.target.files;

    if (files.length === 1 && droppedFile===null) {
      const file = files[0];
      const fileName = file.name;
  
      // Check if the file is a CSV (text/csv)
      if (file.type === 'text/csv') {
          setDroppedFile({ name: fileName, file:file });
      } else {
        // Showing a toast message indicating that only CSV files are allowed.
        setShowToast({
          label: "Only CSV files are allowed.",
          isError: true,
        });
        closeToast();
      }
    } else {
      // Showing a toast message indicating that only one file is allowed at a time.
      setShowToast({
        label: "Only one file is allowed at a time.",
        isError: true,
      });
      closeToast();
    }
  };

  const handleButtonClick = () => {
    fileInputRef.current.click();
  };
  const handleRemoveFile = () => {
    setDroppedFile(null);
    // You can also perform any additional cleanup or actions here.
  };


  const responseToast = () => {

    if (response?.message != null ) {
      setShowToast({
        label: response?.message,
        isError: false,
      });
      closeToast();
    }
     else {
      setShowToast({
        label: "Ingestion failed",
        isError: true,
      });
      closeToast();
     }
  }
 useEffect (() => {
  if (response) { // Check if response is not null or undefined
    responseToast();
  }
 },[response])
  
  const onsubmit = async () => {
    if (droppedFile?.file == null) {
      setShowToast({
        label: "Please choose a file to ingest.",
        isError: true,
      });
      closeToast();
    }
    else {

      setIsLoading(true);
      try{
      const formData = new FormData();
      formData.append("file", droppedFile.file);

      switch (ingestionType) {
        case "Facility":
          formData.append(
            "DHIS2IngestionRequest",
            JSON.stringify({
              tenantId: Digit.ULBService.getCurrentTenantId(),
              dataType: "Facility",
              requestInfo: {
                userInfo: Digit.UserService.getUser().info,
              },
            })
          );
          const { data: facilityRes} = await Digit.IngestionService.facility(formData);
          setResponse(facilityRes);

          break;

        case "OU":
          formData.append(
            "DHIS2IngestionRequest",
            JSON.stringify({
              tenantId: Digit.ULBService.getCurrentTenantId(),
              requestInfo: {
                userInfo: Digit.UserService.getUser().info,
              },
            })
          );
          const ouRes = await Digit.IngestionService.ou(formData);
          setResponse(ouRes);
          break;

        case "User":
          formData.append(
            "DHIS2IngestionRequest",
            JSON.stringify({
              tenantId: Digit.ULBService.getCurrentTenantId(),
              dataType: "Users",
              requestInfo: {
                userInfo: Digit.UserService.getUser(),
              },
            })
          );
          const {data: userRes} = await Digit.IngestionService.user(formData);
          setResponse(userRes);
          break;

          case "Boundary":
            formData.append(
              "DHIS2IngestionRequest",
              JSON.stringify({
                tenantId: Digit.ULBService.getCurrentTenantId(),
                requestInfo: {
                  userInfo: Digit.UserService.getUser().info,
                },
              })
            );
            const boundaryRes = await Digit.IngestionService.boundary(formData);
            setResponse(boundaryRes);
            break;

            case "Project":
              formData.append(
                "DHIS2IngestionRequest",
                JSON.stringify({
                  tenantId: Digit.ULBService.getCurrentTenantId(),
                  requestInfo: {
                    userInfo: Digit.UserService.getUser().info,
                  },
                })
              );
              const projectRes = await Digit.IngestionService.project(formData);
              setResponse(projectRes);
              break;

        default:
          setShowToast({
            label: "Unsupported ingestion type.",
            isError: true,
          });
          closeToast();
          return;
      }
    }
    catch(error){
        // Handle errors here
        console.error(error);
        setShowToast({
          label: "Error occurred during ingestion.",
          isError: true,
        });
        closeToast();
      } finally {
        setIsLoading(false); // Set loading state to false when ingestion completes (either success or failure)
      }
  }
  }

  return (
    <div>
      <Header>{ingestionType} Ingestion</Header>
      <div className={`drop-area ${isDragActive ? 'active' : ''}`} onDragEnter={handleDragEnter} onDragOver={(e) => e.preventDefault()} onDragLeave={handleDragLeave} onDrop={handleFileInput}>
        {droppedFile ? (
          <div>
          <p className="drag-drop-tag">File: {droppedFile.name}</p>
          <button className="remove-button" onClick={handleRemoveFile }>Remove</button>
          </div>
        ) : (
          <div>
          <p className="drag-drop-tag">Drag and drop your file here or</p>
          <button className="upload-file-button" onClick={handleButtonClick}>Browse files</button>
        <input 
        type="file" 
        ref={fileInputRef} 
        style={{ display: 'none' }} 
        accept=".csv"
        onChange={handleFileInput} />
          </div>
        )}
         {isLoading && <Loader message="Fetching data, please wait..." />} 
      {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)} />}
        {/* {showToast && <Toast label={showToast.label} error={showToast?.isError} isDleteBtn={true} onClose={() => setShowToast(null)}></Toast>} */}
      </div>
      <ActionBar>
      <SubmitBar label={"Submit"} 
      onSubmit={onsubmit}
      />
      </ActionBar>
    </div>
  );
}

export default FileDropArea;
