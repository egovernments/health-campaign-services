export const processHierarchyAndData = (hierarchy, allData) => {
  const hierarchyLists = {};
  let hierarchicalData = {};
  try {
    // Process hierarchy
    hierarchy.forEach((item) => {
      hierarchyLists[item.boundaryType] = [];
    });

    // Process all sets of data
    allData.forEach((data) => {
      const dataHierarchicalData = {};

      // Process data for this set
      data.slice(1).forEach((row) => {
        // Exclude the header row
        let currentNode = dataHierarchicalData;
        let parent = null;
        hierarchy.forEach((item, index) => {
          const boundaryType = item.boundaryType;
          const dataIndex = data?.[0].indexOf(boundaryType);
          if (dataIndex === -1) return;
          const cellValue = row[dataIndex];
          if (!cellValue) return;
          // Populate hierarchy lists
          if (!hierarchyLists[boundaryType].includes(cellValue) && cellValue !== null && cellValue !== "" && cellValue !== undefined) {
            hierarchyLists[boundaryType].push(cellValue);
          }

          // Populate hierarchical data
          if (!currentNode[cellValue]) {
            currentNode[cellValue] = {
              name: cellValue,
              boundaryType: boundaryType,
              children: {},
              data: null,
            };
          }

          // Assign row data to the correct hierarchical level
          if (cellValue) {
            if (index === hierarchy.length - 1) {
              currentNode[cellValue].data = createDataObject(data[0], row);
            } else if (index + 1 < hierarchy.length) {
              let nextHierarchyList = hierarchy.slice(index + 1);
              let check = true;
              nextHierarchyList.forEach((e) => {
                const boundaryType = e.boundaryType;
                const dataIndex = data?.[0].indexOf(boundaryType);
                if (dataIndex === -1) return;
                check = check && !row[dataIndex];
              });
              if (check) currentNode[cellValue].data = createDataObject(data[0], row);
            }
          }
          currentNode = currentNode[cellValue].children;
        });
      });

      // Merge dataHierarchicalData into hierarchicalData
      hierarchicalData = mergeHierarchicalData(hierarchicalData, dataHierarchicalData);
    });

    // Remove null element from children of each province
    Object.values(hierarchicalData).forEach((country) => {
      if (country.children[null]) {
        country.data = country.children[null].data;
        country.children[null] = undefined;
      }
    });
  } catch (error) {
    console.error("Error in processing hierarchy and uploaded data: ", error.message);
    // Return empty objects in case of error
    return { hierarchyLists: {}, hierarchicalData: {} };
  }

  return { hierarchyLists, hierarchicalData };
};

// Function to merge two hierarchical data objects
const mergeHierarchicalData = (data1, data2) => {
  for (const [key, value] of Object.entries(data2)) {
    if (!data1[key]) {
      if (!value.data) value.data = {};
      data1[key] = value || {};
    } else {
      data1[key].data = value.data; // Merge data
      mergeHierarchicalData(data1[key].children, value.children); // Recursively merge children
    }
    if (data1[key].data?.feature) {
      const { feature, ...temp } = value.data ? _.cloneDeep(value.data) : {};
      data1[key].data.feature.properties = { ...data1[key].data?.feature?.properties, ...temp };
    }
  }
  return data1;
};

// Function to create a data object with key-value pairs from headers and row data
const createDataObject = (headers, row) => {
  const dataObject = {};
  headers.forEach((header, index) => {
    dataObject[header] = row[index];
  });
  return dataObject;
};

// Find parent in hierarchy
export const findParent = (name, hierarchy, parent, accumulator = []) => {
  if (!name || !hierarchy) return null;
  for (let key in hierarchy) {
    if (hierarchy[key]?.name == name) {
      accumulator.push(parent);
    }
    if (hierarchy[key]?.children) {
      let response = findParent(name, hierarchy[key]?.children, hierarchy[key], accumulator);
      if (response)
        response.forEach((item) => {
          if (!accumulator.includes(item)) {
            accumulator.push(item);
          }
        });
    } else {
      return accumulator;
    }
  }
  return accumulator;
};

/**
 *
 * @param {Array of parents} parents
 * @param {hierarchycal Object data} hierarchy
 * @returns An Array containing all the cummulative children
 */
export const findChildren = (parents, hierarchy) => {
  const hierarchyTraveller = (parents, hierarchy, accumulator = {}) => {
    let tempData = [];
    if (accumulator && Object.keys(accumulator).length !== 0)
      tempData = {
        ...accumulator,
        ...hierarchy.reduce((data, item) => {
          if (parents.includes(item?.name) && item?.children) {
            for (const key in item.children) {
              if (!data[key]) {
                data[key] = item.children[key];
              }
            }
          }
          return data;
        }, {}),
      };
    else
      tempData = hierarchy.reduce((data, item) => {
        if (parents.includes(item?.name) && item?.children) {
          for (const key in item.children) {
            if (!data[key]) {
              data[key] = item.children[key];
            }
          }
        }
        return data;
      }, {});
    for (let parent of hierarchy) {
      if (parent?.children) tempData = hierarchyTraveller(parents, Object.values(parent?.children), tempData);
    }
    return tempData;
  };
  return hierarchyTraveller(parents, Object.values(hierarchy), {});
};

// Fetched data from tree
export const fetchDropdownValues = (boundaryData, hierarchy, boundarySelections, changedBoundaryType) => {
  if (
    !hierarchy ||
    !boundaryData ||
    !boundarySelections ||
    hierarchy.length === 0 ||
    Object.keys(hierarchy).length === 0 ||
    Object.keys(boundaryData).length === 0
  )
    return [];
  let TempHierarchy = _.cloneDeep(hierarchy);
  if (!boundarySelections || Object.values(boundarySelections)?.every((item) => item?.length === 0)) {
    for (let i in TempHierarchy) {
      if (i === "0") {
        TempHierarchy[0].dropDownOptions = findByBoundaryType(
          TempHierarchy?.[0]?.boundaryType,
          Object.values(boundaryData)?.[0]?.hierarchicalData
        ).map((data, index) => ({
          name: data,
          code: data,
          boundaryType: TempHierarchy?.[0]?.boundaryType,
          parentBoundaryType: undefined,
        }));
      } else TempHierarchy[i].dropDownOptions = [];
    }
  } else {
    const currentHierarchy = findCurrentFilteredHierarchy(Object.values(boundaryData)?.[0]?.hierarchicalData, boundarySelections, TempHierarchy);
    let currentDropdownIndex = 0;
    hierarchy.forEach((e, index) => {
      if (e && e?.boundaryType == changedBoundaryType) {
        // && boundarySelections && boundarySelections[e.boundaryType] && boundarySelections[e.boundaryType].length !== 0) {
        currentDropdownIndex = index;
      }
    });
    Object.entries(boundarySelections)?.forEach(([key, value]) => {
      let currentindex = hierarchy.findIndex((e) => e?.boundaryType === key);
      if (currentDropdownIndex !== currentindex) return;
      let childIndex = hierarchy.findIndex((e) => e?.parentBoundaryType === key);
      if (childIndex == -1) return;
      if (TempHierarchy?.[childIndex]) {
        let newDropDownValuesForChild = [];
        for (const element of value) {
          let tempStore = Object.values(findChildren([element.name], currentHierarchy)).map((value) => ({
            name: value?.name,
            code: value?.name,
            parent: element,
            boundaryType: TempHierarchy[childIndex]?.boundaryType,
            parentBoundaryType: TempHierarchy[childIndex]?.parentBoundaryType,
          }));
          if (tempStore) newDropDownValuesForChild.push(...tempStore);
        }
        // if (TempHierarchy[childIndex].dropDownOptions)
        //   TempHierarchy[childIndex].dropDownOptions = [...TempHierarchy[childIndex].dropDownOptions, ...newDropDownValuesForChild];
        TempHierarchy[childIndex].dropDownOptions = newDropDownValuesForChild;
      }
    });
  }
  return TempHierarchy;
};

const findByBoundaryType = (boundaryType, hierarchy) => {
  for (let [key, value] of Object.entries(hierarchy)) {
    if (value?.boundaryType === boundaryType) return Object.keys(hierarchy).filter(Boolean);
    if (value?.children) return findByBoundaryType(boundaryType, value?.children);
    return [];
  }
  return [];
};

// makes a tree with the boundary selections as there might be duplicates in different branches that are not yet selected
const findCurrentFilteredHierarchy = (hierarchyTree, boundarySelections, hierarchy) => {
  const newtree = constructNewHierarchyTree(hierarchy, hierarchyTree, boundarySelections);
  return newtree;
};

const constructNewHierarchyTree = (hierarchy, oldTree, boundarySelection, level = 0) => {
  // let newTree = { ...oldTree }; // Initialize a new hierarchy tree
  let newTree = {}; // Initialize a new hierarchy tree
  if (!hierarchy?.[level]) return;
  const levelName = hierarchy[level].boundaryType;

  // Get the selections for this level from the boundary selection object
  const selections = boundarySelection[levelName] || [];
  // If there are selections for this level
  if (selections.length > 0) {
    // Construct the new hierarchy tree based on selections
    for (const selection of selections) {
      const { name } = selection;
      // If the selection exists in the existing hierarchy tree
      if (oldTree[name]) {
        // Add the selected division to the new hierarchy tree
        newTree[name] = { ...oldTree[name] };
        // If there are children, recursively construct the children
        if (oldTree[name].children) {
          oldTree[name].children;
          const nonNullObject = Object.entries(oldTree[name].children).reduce((acc, [key, value]) => {
            if (value.name !== null) {
              acc[key] = value;
            }
            return acc;
          }, {});
          newTree[name].children = constructNewHierarchyTree(hierarchy, nonNullObject, boundarySelection, level + 1);
        }
      }
    }
  } else {
    const nonNullObject = Object.entries(oldTree).reduce((acc, [key, value]) => {
      if (value.name !== null) {
        acc[key] = value;
      }
      return acc;
    }, {});
    newTree = nonNullObject;
  }

  return newTree;
};

// Recursively calculates aggregate values for numerical properties within the `data` objects of each node in a hierarchical tree structure.
// Updates the `properties` object within the `feature` object of each node with the aggregate values, if present.
export const calculateAggregateForTree = (tree) => {
  try {
    function calculateAggregate(node) {
      if (!node.children || Object.keys(node.children).length === 0) {
        // if the node has no children, return a new node with its own data
        return { ...node, data: { ...node.data } };
      }

      // Recursively calculate aggregate values for each child
      const newChildren = {};

      for (const childKey in node.children) {
        const child = node.children[childKey];
        const newChild = calculateAggregate(child);
        newChildren[childKey] = newChild;
      }

      // Aggregate numerical values dynamically
      const aggregate = {};
      for (const childKey in newChildren) {
        const child = newChildren[childKey];
        for (const prop in child.data) {
          if (typeof child.data[prop] === "number") {
            aggregate[prop] = (aggregate[prop] || 0) + child.data[prop];
          }
        }
      }

      // Create a new node with updated data
      const newNode = {
        ...node,
        data: { ...node.data, ...aggregate },
        children: newChildren,
      };

      // Update properties in the feature object
      if (newNode.data.feature) {
        newNode.data.feature.properties = { ...newNode.data.feature.properties, ...aggregate };
      }

      return newNode;
    }

    const newTree = {};

    // Iterate over each node object
    for (const nodeKey in tree) {
      const node = tree[nodeKey];
      // Calculate aggregate values for the current node
      const newNode = calculateAggregate(node);
      // Add the updated node to the new tree
      newTree[nodeKey] = newNode;
    }

    return newTree;
  } catch (error) {
    console.error("Failed to calculate treenode aggregates");
    return {};
  }
};
