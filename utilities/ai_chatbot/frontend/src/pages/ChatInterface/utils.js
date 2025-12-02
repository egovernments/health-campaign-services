import dayjs from 'dayjs';

// To Download the table
export const exportToCSV = (data, filename = 'table_data.csv') => {
  if (!data || data.length === 0) {
    console.warn('No data available to export');
    return;
  }

  const escapeValue = (value) => {
    // Convert value to string if it's not already a string
    const stringValue = String(value);
    // Wrap value in double quotes and escape existing double quotes within the value
    return `"${stringValue.replace(/"/g, '""')}"`;
  };

  const csvContent = [Object.keys(data[0]).map(escapeValue).join(','), ...data.map((row) => Object.values(row).map(escapeValue).join(','))].join('\n');

  const encodedUri = encodeURIComponent(csvContent);
  const link = document.createElement('a');
  link.setAttribute('href', 'data:text/csv;charset=utf-8,' + encodedUri);
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

// Format text by wrapping matched parts in <strong> tags
export const formatText = (text) => {
  if (typeof text !== 'string') {
    text = String(text ?? '');
  }

  const boldRegex = /(\*\*([^*]+)\*\*|\*([^*]+)\*)/g; // Matches **bold** and *bold*
  const lines = text.split('\n'); // Split text into lines
  const formattedLines = lines.map((line, index) => {
    if (line.startsWith('###')) {
      return <h3 key={index}>{line.slice(3).trim()}</h3>; // Convert to h3
    }

    // Handle bold formatting within regular text
    const parts = [];
    let lastIndex = 0;
    let match;

    while ((match = boldRegex.exec(line)) !== null) {
      if (match.index > lastIndex) {
        parts.push(line.slice(lastIndex, match.index));
      }

      parts.push(
        <strong key={`${index}-${match.index}`}>
          {match[2] || match[3]} {/* Capture text inside ** or * */}
        </strong>
      );

      lastIndex = boldRegex.lastIndex;
    }
    if (lastIndex < line.length) {
      parts.push(line.slice(lastIndex));
    }

    return <p key={index}>{parts}</p>; // Wrap in <p>
  });

  return formattedLines;
};

// Add this function to highlight text
export const highlightText = (text, searchTerm) => {
  if (!searchTerm) return text;
  const regex = new RegExp(`(${escapeRegExp(searchTerm)})`, 'gi');
  const parts = String(text).split(regex);
  return parts.map((part, index) =>
    regex.test(part) ? (
      <span key={index} style={{ backgroundColor: '#ffeb3b' }}>
        {part}
      </span>
    ) : (
      part
    )
  );
};

export function toTitleCase(str) {
  return str
    .replace(/[_\s]+/g, ' ') // Replace underscores or multiple spaces with a single space
    .toLowerCase()
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

// Utility function to truncate long strings
const truncateString = (str, maxLength) => {
  if (str.length > maxLength) {
    return str.substring(0, maxLength - 3) + '...'; // Truncate and add ellipses
  }
  return str;
};

const formatNumber = (value) => {
  const displayValue = Number(value.toFixed(2));

  if (displayValue >= 1_000_000) {
    return (displayValue / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M';
  } else if (displayValue >= 1_000) {
    return (displayValue / 1_000).toFixed(1).replace(/\.0$/, '') + 'K';
  } else {
    return displayValue
      .toFixed(2)
      .replace(/\.00$/, '')
      .replace(/(\.\d)0$/, '$1');
  }
};

// Add this helper function to escape regex special characters
function escapeRegExp(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Prepare data for Bar Chart
export const getBarChartOptions = (data) => {
  if (!Array.isArray(data) || data.length === 0) return {};

  const columns = Object.keys(data[0]);
  if (columns.length < 2) return {};

  const firstIsNumeric = typeof data[0][columns[0]] === 'number';
  const secondIsNumeric = typeof data[0][columns[1]] === 'number';

  let xData, yData, xLabel, yLabel;

  if (firstIsNumeric && !secondIsNumeric) {
    // Swap: Y-axis must be numeric
    xData = data.map((item) => item[columns[1]]);
    yData = data.map((item) => item[columns[0]]);
    xLabel = toTitleCase(columns[1]);
    yLabel = toTitleCase(columns[0]);
  } else {
    // Either second is numeric, or both are numeric → X = first, Y = second
    xData = data.map((item) => item[columns[0]]);
    yData = data.map((item) => item[columns[1]]);
    xLabel = toTitleCase(columns[0]);
    yLabel = toTitleCase(columns[1]);
  }

  return {
    title: { text: `${yLabel} vs ${xLabel}` },
    tooltip: {
      trigger: 'item',
      z: 150,
      formatter: (params) => {
        const truncatedName = truncateString(params.name, 15);
        return `${params.seriesName} <br/>${truncatedName}: ${formatNumber(params.value)}`;
      },
      backgroundColor: 'rgba(255, 255, 255, 0.8)',
      borderColor: '#ccc',
      borderWidth: 1,
      padding: [5, 10],
      textStyle: { fontSize: 12, color: '#333' },
      position: (point) => [point[0] + 10, point[1] - 10],
    },
    xAxis: {
      type: typeof xData[0] === 'number' ? 'value' : 'category',
      data: xData,
      nameTextStyle: { padding: [0, 0, 0, 10] },
      axisLabel: {
        margin: 10,
        fontSize: 12,
        formatter: (value) => (typeof value === 'string' ? truncateString(value, 10) : value),
      },
    },
    yAxis: {
      type: 'value',
      name: yLabel,
      axisLabel: { formatter: (value) => formatNumber(value) },
    },
    series: [
      {
        name: yLabel,
        type: 'bar',
        data: yData,
        barWidth: '30%',
        label: {
          show: true,
          position: 'top',
          formatter: (params) => formatNumber(params.value),
          fontSize: 10,
          color: '#000',
        },
      },
    ],
  };
};

// Prepare data for Line Chart
export const getLineChartOptions = (data) => {
  if (!Array.isArray(data) || data.length === 0) return {};

  const columns = Object.keys(data[0]);
  if (columns.length < 2) return {};

  const firstIsNumeric = typeof data[0][columns[0]] === 'number';
  const secondIsNumeric = typeof data[0][columns[1]] === 'number';

  let xData, yData, xLabel, yLabel;

  if (firstIsNumeric && !secondIsNumeric) {
    // Swap so Y-axis is numeric
    xData = data.map((item) => item[columns[1]]);
    yData = data.map((item) => item[columns[0]]);
    xLabel = toTitleCase(columns[1]);
    yLabel = toTitleCase(columns[0]);
  } else {
    // Second numeric, or both numeric → X = first, Y = second
    xData = data.map((item) => item[columns[0]]);
    yData = data.map((item) => item[columns[1]]);
    xLabel = toTitleCase(columns[0]);
    yLabel = toTitleCase(columns[1]);
  }

  return {
    title: { text: `${yLabel} vs ${xLabel}` },
    tooltip: {
      trigger: 'item',
      z: 150,
      formatter: (params) => {
        const truncatedName = truncateString(params.name, 15);
        return `${params.seriesName} <br/>${truncatedName}: ${formatNumber(params.value)}`;
      },
      backgroundColor: 'rgba(255, 255, 255, 0.8)',
      borderColor: '#ccc',
      borderWidth: 1,
      padding: [5, 10],
      textStyle: { fontSize: 12, color: '#333' },
      position: (point) => [point[0] + 10, point[1] - 10],
    },
    xAxis: {
      type: typeof xData[0] === 'number' ? 'value' : 'category',
      data: xData,
      nameTextStyle: { padding: [0, 0, 0, 10] },
      axisLabel: {
        margin: 10,
        fontSize: 12,
        formatter: (value) => (typeof value === 'string' ? truncateString(value, 10) : value),
      },
    },
    yAxis: {
      type: 'value',
      name: yLabel,
      axisLabel: { formatter: (value) => formatNumber(value) },
    },
    series: [
      {
        name: yLabel,
        type: 'line',
        data: yData,
        label: {
          show: true,
          position: 'top',
          formatter: (params) => formatNumber(params.value),
          fontSize: 10,
          color: '#000',
        },
      },
    ],
  };
};

// Prepare data for Area Chart
export const getAreaChartOptions = (data) => {
  if (!Array.isArray(data) || data.length === 0) return {};

  const columns = Object.keys(data[0]);
  if (columns.length < 2) return {};

  const firstIsNumeric = typeof data[0][columns[0]] === 'number';
  const secondIsNumeric = typeof data[0][columns[1]] === 'number';

  let xData, yData, xLabel, yLabel;

  if (firstIsNumeric && !secondIsNumeric) {
    // Swap so Y-axis is numeric
    xData = data.map((item) => item[columns[1]]);
    yData = data.map((item) => item[columns[0]]);
    xLabel = toTitleCase(columns[1]);
    yLabel = toTitleCase(columns[0]);
  } else {
    // Second numeric, or both numeric → X = first, Y = second
    xData = data.map((item) => item[columns[0]]);
    yData = data.map((item) => item[columns[1]]);
    xLabel = toTitleCase(columns[0]);
    yLabel = toTitleCase(columns[1]);
  }

  return {
    title: { text: `${yLabel} vs ${xLabel}` },
    tooltip: {
      trigger: 'item',
      z: 150,
      formatter: (params) => {
        const truncatedName = truncateString(params.name, 15);
        return `${params.seriesName} <br/>${truncatedName}: ${formatNumber(params.value)}`;
      },
      backgroundColor: 'rgba(255, 255, 255, 0.8)',
      borderColor: '#ccc',
      borderWidth: 1,
      padding: [5, 10],
      textStyle: { fontSize: 12, color: '#333' },
      position: (point) => [point[0] + 10, point[1] - 10],
    },
    xAxis: {
      type: typeof xData[0] === 'number' ? 'value' : 'category',
      data: xData,
      axisLabel: {
        formatter: (value) => (typeof value === 'string' ? truncateString(value, 10) : value),
      },
    },
    yAxis: {
      type: 'value',
      name: yLabel,
      axisLabel: { formatter: (value) => formatNumber(value) },
    },
    series: [
      {
        name: yLabel,
        type: 'line',
        areaStyle: {},
        data: yData,
        label: {
          show: true,
          position: 'top',
          formatter: function (params) {
            return formatNumber(params.value);
          },
          fontSize: 10,
          color: '#000',
        },
      },
    ],
  };
};

export const formatDateValues = (data) => {
  if (!Array.isArray(data)) return [];

  return data.map((row) => {
    const newRow = { ...row };
    for (const key in newRow) {
      const value = newRow[key];

      if (typeof value === 'string' && dayjs(value).isValid() && value.match(/^\d{4}-\d{2}-\d{2}/)) {
        const dateObj = dayjs(value);
        const hour = dateObj.hour();
        const minute = dateObj.minute();
        const second = dateObj.second();

        const isMidnight = hour === 0 && minute === 0 && second === 0;

        newRow[key] = isMidnight ? dateObj.format('DD MMM YYYY') : dateObj.format('DD MMM YYYY hh:mm A');
      }
    }
    return newRow;
  });
};

function isNumeric(val) {
  return typeof val === 'number' && !isNaN(val);
}

export function checkIfGraphData(data) {
  if (!Array.isArray(data) || data.length < 2) return false;

  const firstRow = data[0];
  const columns = Object.keys(firstRow);

  if (columns.length !== 2) return false;

  const col1 = columns[0];
  const col2 = columns[1];

  // Check if at least one column has numeric values
  const hasNumeric = data.some((row) => isNumeric(row[col1]) || isNumeric(row[col2]));

  return hasNumeric;
}
