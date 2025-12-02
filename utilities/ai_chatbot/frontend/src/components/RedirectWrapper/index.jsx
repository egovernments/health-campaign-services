import { v4 as uuidv4 } from "uuid";
import { useEffect, useState, useContext } from "react";
import { useHistory } from "react-router-dom";
import { getAllConfigurations, getDefaultModel } from "../../services/ProductApis";
import AuthContext from "../../state/authContex";

const RedirectWrapper = ({ Component }) => {
  const [datasources, setDatasources] = useState([]);
  const history = useHistory();
  const { isLoggedIn, roles } = useContext(AuthContext);

  const fetchData = async () => {
    try {
      // console.log("Fetching datasources...");
      const response = await getAllConfigurations();
      setDatasources(response?.data);
      // console.log("Datasources fetched:", response.data);
    } catch (error) {
      console.error("Error fetching datasources:", error);
    }
  };

  const fetchDefaultModel = async () => {
    try {
      const response = await getDefaultModel();
      if (response?.data?.name) {
        localStorage.setItem("defaultModel", response.data.name);
        // console.log("Default model set:", response.data.name);
      }
    } catch (error) {
      console.error("Error fetching default model:", error);
    }
  };

  useEffect(() => {
    if (isLoggedIn) fetchData();
  }, [isLoggedIn]);

  useEffect(() => {
    if (!isLoggedIn) {
      // console.log("User not logged in yet. Waiting...");
      return;
    }

    if (datasources.length === 1) {
      const sessionId = uuidv4();
      fetchDefaultModel();
      history.push(`/chat?dsname=${datasources[0].name}&dsId=${datasources[0].id}&sessionId=${sessionId}`);
    } else if (datasources.length > 1) {
      history.push("/");
    }
  }, [datasources, isLoggedIn]);

  return <Component />;
};

export default RedirectWrapper;
