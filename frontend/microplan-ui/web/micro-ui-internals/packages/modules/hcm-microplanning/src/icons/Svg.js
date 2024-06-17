import React from "react";

export const PopulationSvg = (style) => {
  return `
        <svg width="50" height="50" viewBox="0 0 18 26" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M9 0C4.03714 0 0 4.082 0 9.1C0 15.925 9 26 9 26C9 26 18 15.925 18 9.1C18 4.082 13.9629 0 9 0Z" fill="${
          style?.fill || "rgba(176, 176, 176, 1)"
        }"/>
        <g clip-path="url(#clip0_6283_43793)">
        <path d="M11 8.5C11.83 8.5 12.495 7.83 12.495 7C12.495 6.17 11.83 5.5 11 5.5C10.17 5.5 9.5 6.17 9.5 7C9.5 7.83 10.17 8.5 11 8.5ZM7 8.5C7.83 8.5 8.495 7.83 8.495 7C8.495 6.17 7.83 5.5 7 5.5C6.17 5.5 5.5 6.17 5.5 7C5.5 7.83 6.17 8.5 7 8.5ZM7 9.5C5.835 9.5 3.5 10.085 3.5 11.25V12.5H10.5V11.25C10.5 10.085 8.165 9.5 7 9.5ZM11 9.5C10.855 9.5 10.69 9.51 10.515 9.525C11.095 9.945 11.5 10.51 11.5 11.25V12.5H14.5V11.25C14.5 10.085 12.165 9.5 11 9.5Z" fill="white"/>
        </g>
        <defs>
        <clipPath id="clip0_6283_43793">
        <rect width="12" height="12" fill="white" transform="translate(3 3)"/>
        </clipPath>
        </defs>
        </svg>
    `;
};

export const HelpOutlineIcon = ({ className = "", fill = "", style = {} }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" className={className} style={style}>
    <g clip-path="url(#clip0_52342_113207)">
      <path
        d="M11 18H13V16H11V18ZM12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20ZM12 6C9.79 6 8 7.79 8 10H10C10 8.9 10.9 8 12 8C13.1 8 14 8.9 14 10C14 12 11 11.75 11 15H13C13 12.75 16 12.5 16 10C16 7.79 14.21 6 12 6Z"
        fill={fill}
      />
    </g>
    <defs>
      <clipPath id="clip0_52342_113207">
        <rect width="24" height="24" fill={fill} />
      </clipPath>
    </defs>
  </svg>
);

export const DefaultMapMarkerSvg = (style) => {
  return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" version="1.1" width="50" height="50" viewBox="0 0 256 256" xml:space="preserve">
  <g style="stroke: none; stroke-width: 0; stroke-dasharray: none; stroke-linecap: butt; stroke-linejoin: miter; stroke-miterlimit: 10; fill: none; fill-rule: nonzero; opacity: 1;" transform="translate(1.4065934065934016 1.4065934065934016) scale(2.81 2.81)" >
    <path d="M 45 90 c -1.415 0 -2.725 -0.748 -3.444 -1.966 l -4.385 -7.417 C 28.167 65.396 19.664 51.02 16.759 45.189 c -2.112 -4.331 -3.175 -8.955 -3.175 -13.773 C 13.584 14.093 27.677 0 45 0 c 17.323 0 31.416 14.093 31.416 31.416 c 0 4.815 -1.063 9.438 -3.157 13.741 c -0.025 0.052 -0.053 0.104 -0.08 0.155 c -2.961 5.909 -11.41 20.193 -20.353 35.309 l -4.382 7.413 C 47.725 89.252 46.415 90 45 90 z" style="stroke: none; stroke-width: 1; stroke-dasharray: none; stroke-linecap: butt; stroke-linejoin: miter; stroke-miterlimit: 10; fill: ${
      style.fill ? style.fill : "#42BBFF"
    }; fill-rule: nonzero; opacity: 1;" transform=" matrix(1 0 0 1 0 0) " stroke-linecap="round" />
    <path d="M 45 45.678 c -8.474 0 -15.369 -6.894 -15.369 -15.368 S 36.526 14.941 45 14.941 c 8.474 0 15.368 6.895 15.368 15.369 S 53.474 45.678 45 45.678 z" style="stroke: none; stroke-width: 1; stroke-dasharray: none; stroke-linecap: butt; stroke-linejoin: miter; stroke-miterlimit: 10; fill: rgb(255,255,255); fill-rule: nonzero; opacity: 1;" transform=" matrix(1 0 0 1 0 0) " stroke-linecap="round" />
  </g>
  </svg>`;
};


export const WarehouseMarker = ({
  className = "",
  fill = "white",
  fillBackground = "#42BBFF",
  style = {},
  width = "3.125rem",
  height = "3.125rem",
}) => {
  return `
    <svg width="${width}" height="${height}" viewBox="0 0 18 26" fill="none" xmlns="http://www.w3.org/2000/svg" style=${style} className=${className}>
      <path d="M9 0C4.03714 0 0 4.082 0 9.1C0 15.925 9 26 9 26C9 26 18 15.925 18 9.1C18 4.082 13.9629 0 9 0Z" fill=${fillBackground} />
      <g clip-path="url(#clip0_5909_17198)">
        <path d="M9 4.5L5 7.5V13.5H7.5V10H10.5V13.5H13V7.5L9 4.5Z" fill=${fill} />
      </g>
      <defs>
        <clipPath id="clip0_5909_17198">
          <rect width="12" height="12" fill="white" transform="translate(3 3)" />
        </clipPath>
      </defs>
    </svg>
    `;
};

export const Warehouse = ({ className = "", fill = "white", fillBackground = "#42BBFF", style = {}, width = "1.5rem", height = "1.5rem" }) => {
  return (
    <svg width={width} height={height} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={style} className={className}>
      <rect x="0.5" y="0.5" width="23" height="23" rx="3.5" fill={fillBackground} stroke="#D6D5D4" />
      <g clip-path="url(#clip0_6391_89286)">
        <path d="M12.0001 6L6.66675 10V18H10.0001V13.3333H14.0001V18H17.3334V10L12.0001 6Z" fill={fill} />
      </g>
      <defs>
        <clipPath id="clip0_6391_89286">
          <rect width="16" height="16" fill={fill} transform="translate(4 4)" />
        </clipPath>
      </defs>
    </svg>
  );
};

export const Church = ({ className = "", fill = "white", fillBackground = "#064466", style = {}, width = "1.5rem", height = "1.5rem" }) => {
  return (
    <svg width={width} height={height} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={style} className={className}>
      <rect x="0.5" y="0.5" width={width} height={height} rx="3.5" fill={fillBackground} stroke="#D6D5D4" />
      <g clip-path="url(#clip0_6391_89292)">
        <path
          d="M12.0942 10.9979C12.3785 10.9984 12.6549 11.0897 12.8813 11.2578L15.0118 12.847V11.217C15.0118 11.084 14.961 10.9559 14.8694 10.8577L12.6443 8.4715V6.52864H14.1296V5.4525H12.6443V4H11.544V5.4525H10.0588V6.52864H11.544V8.4715L9.3189 10.8577C9.22729 10.9559 9.17651 11.084 9.17651 11.217V12.8471L11.307 11.2579C11.5334 11.0897 11.8098 10.9984 12.0942 10.9979Z"
          fill={fill}
        />
        <path
          d="M18.3674 13.4594L12.1162 8.69158C12.0828 8.66613 12.042 8.65234 12 8.65234C11.9581 8.65234 11.9172 8.66613 11.8839 8.69158L5.63265 13.4594C5.60929 13.4772 5.59211 13.5019 5.58353 13.53C5.57494 13.5581 5.57538 13.5881 5.58478 13.616C5.59418 13.6438 5.61207 13.668 5.63593 13.6851C5.65979 13.7022 5.68842 13.7114 5.71779 13.7114H6.84855V20.0009H10.1185V16.0352C10.1185 15.2027 10.9609 14.3397 12 14.3397C13.0391 14.3397 13.8815 15.2028 13.8815 16.0352V20.0009H17.1515V13.7114H18.2822C18.3116 13.7114 18.3402 13.7022 18.3641 13.6851C18.388 13.668 18.4059 13.6438 18.4153 13.616C18.4247 13.5881 18.4251 13.5581 18.4165 13.53C18.4079 13.5019 18.3907 13.4772 18.3674 13.4594Z"
          fill={fill}
        />
      </g>
      <defs>
        <clipPath id="clip0_6391_89292">
          <rect width="16" height="16" fill={fill} transform="translate(4 4)" />
        </clipPath>
      </defs>
    </svg>
  );
};

export const School = ({ className = "", fill = "white", fillBackground = "#FF7B42", style = {}, width = "1.5rem", height = "1.5rem" }) => {
  return (
    <svg width={width} height={height} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={style} className={className}>
      <rect x="0.5" y="0.5" width={width} height={height} rx="3.5" fill={fillBackground} stroke="#D6D5D4" />
      <g clip-path="url(#clip0_6393_89315)">
        <g clip-path="url(#clip1_6393_89315)">
          <path
            d="M14 11.332V7.33203L12 5.33203L10 7.33203V8.66536H6V17.9987H18V11.332H14ZM8.66667 16.6654H7.33333V15.332H8.66667V16.6654ZM8.66667 13.9987H7.33333V12.6654H8.66667V13.9987ZM8.66667 11.332H7.33333V9.9987H8.66667V11.332ZM12.6667 16.6654H11.3333V15.332H12.6667V16.6654ZM12.6667 13.9987H11.3333V12.6654H12.6667V13.9987ZM12.6667 11.332H11.3333V9.9987H12.6667V11.332ZM12.6667 8.66536H11.3333V7.33203H12.6667V8.66536ZM16.6667 16.6654H15.3333V15.332H16.6667V16.6654ZM16.6667 13.9987H15.3333V12.6654H16.6667V13.9987Z"
            fill={fill}
          />
        </g>
      </g>
      <defs>
        <clipPath id="clip0_6393_89315">
          <rect width="16" height="16" fill="white" transform="translate(4 4)" />
        </clipPath>
        <clipPath id="clip1_6393_89315">
          <rect width="16" height="16" fill={fill} transform="translate(4 4)" />
        </clipPath>
      </defs>
    </svg>
  );
};

export const HealthFacility = ({ className = "", fill = "white", fillBackground = "#0C9219", style = {}, width = "1.5rem", height = "1.5rem" , onClick=null}) => {
  return (
    <svg width={width} height={height} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={style} className={className} onClick={onClick}>
      <rect x="0.5" y="0.5" width={width} height={height} rx="3.5" fill={fillBackground} stroke="#D6D5D4"/>
      <g clip-path="url(#clip0_7657_78223)">
      <path d="M16.6667 6H7.33333C6.6 6 6.00667 6.6 6.00667 7.33333L6 16.6667C6 17.4 6.6 18 7.33333 18H16.6667C17.4 18 18 17.4 18 16.6667V7.33333C18 6.6 17.4 6 16.6667 6ZM16 13.3333H13.3333V16H10.6667V13.3333H8V10.6667H10.6667V8H13.3333V10.6667H16V13.3333Z" 
      fill={fill}/>
      </g>
      <defs>
      <clipPath id="clip0_7657_78223">
      <rect width="16" height="16" fill={fill} transform="translate(4 4)"/>
      </clipPath>
      </defs>
    </svg>
  );
};

export const ChurchMarker = ({ className = "", fill = "white", fillBackground = "#064466", style = {}, width = "3.125rem", height = "3.125rem" }) => {
  return `
  <svg width="${width}" height="${height}" viewBox="0 0 18 26" fill="none" xmlns="http://www.w3.org/2000/svg"  style=${style} className=${className}>
    <path d="M9 0C4.03714 0 0 4.082 0 9.1C0 15.925 9 26 9 26C9 26 18 15.925 18 9.1C18 4.082 13.9629 0 9 0Z" fill="${fillBackground}"/>
    <path d="M9.29453 8.68546C9.52553 8.68588 9.75008 8.76004 9.93404 8.89668L11.665 10.1878V8.86347C11.665 8.75547 11.6237 8.65135 11.5493 8.57156L9.74153 6.63289V5.05441H10.9482V4.18009H9.74153V3H8.84752V4.18009H7.64085V5.05441H8.84752V6.63289L7.03975 8.57156C6.96533 8.65135 6.92407 8.75547 6.92407 8.86347V10.1878L8.65501 8.89668C8.83898 8.76005 9.06352 8.68588 9.29453 8.68546Z" fill="${fill}"/>
    <path d="M14.3913 10.6868L9.31248 6.81313C9.28536 6.79245 9.25221 6.78125 9.2181 6.78125C9.184 6.78125 9.15084 6.79245 9.12373 6.81313L4.04489 10.6868C4.02592 10.7012 4.01196 10.7213 4.00498 10.7441C3.99801 10.7669 3.99836 10.7914 4.006 10.814C4.01364 10.8366 4.02817 10.8562 4.04756 10.8701C4.06694 10.8841 4.09021 10.8915 4.11407 10.8915H5.03276V16.0014H7.68948V12.7795C7.68948 12.1032 8.37386 11.402 9.21811 11.402C10.0623 11.402 10.7467 12.1032 10.7467 12.7795V16.0014H13.4034V10.8915H14.3221C14.346 10.8915 14.3693 10.8841 14.3886 10.8701C14.408 10.8562 14.4226 10.8366 14.4302 10.814C14.4378 10.7914 14.4382 10.7669 14.4312 10.7441C14.4242 10.7213 14.4103 10.7012 14.3913 10.6868Z" fill="${fill}"/>
  </svg>
`;
};

export const SchoolMarker = ({ className = "", fill = "white", fillBackground = "#FF7B42", style = {}, width = "3.125rem", height = "3.125rem" }) => {
  return `
  <svg width="${width}" height="${height}" viewBox="0 0 18 26" fill="none" xmlns="http://www.w3.org/2000/svg"  style=${style} className=${className}>
    <path d="M9 0C4.03714 0 0 4.082 0 9.1C0 15.925 9 26 9 26C9 26 18 15.925 18 9.1C18 4.082 13.9629 0 9 0Z" fill="${fillBackground}"/>
    <g clip-path="url(#clip0_7630_43585)">
    <g clip-path="url(#clip1_7630_43585)">
    <path d="M10.75 8.41797V4.91797L9 3.16797L7.25 4.91797V6.08464H3.75V14.2513H14.25V8.41797H10.75ZM6.08333 13.0846H4.91667V11.918H6.08333V13.0846ZM6.08333 10.7513H4.91667V9.58464H6.08333V10.7513ZM6.08333 8.41797H4.91667V7.2513H6.08333V8.41797ZM9.58333 13.0846H8.41667V11.918H9.58333V13.0846ZM9.58333 10.7513H8.41667V9.58464H9.58333V10.7513ZM9.58333 8.41797H8.41667V7.2513H9.58333V8.41797ZM9.58333 6.08464H8.41667V4.91797H9.58333V6.08464ZM13.0833 13.0846H11.9167V11.918H13.0833V13.0846ZM13.0833 10.7513H11.9167V9.58464H13.0833V10.7513Z" fill="${fill}"/>
    </g>
    </g>
    <defs>
    <clipPath id="clip0_7630_43585">
    <rect width="14" height="14" fill="${fill}" transform="translate(2 2)"/>
    </clipPath>
    <clipPath id="clip1_7630_43585">
    <rect width="14" height="14" fill="${fill}" transform="translate(2 2)"/>
    </clipPath>
    </defs>
  </svg>
`;
};

export const HealthFacilityMarker = ({
  className = "",
  fill = "white",
  fillBackground = "#0C9219",
  style = {},
  width = "3.125rem",
  height = "3.125rem",
}) => {
  return `
  <svg width="${width}" height="${height}" viewBox="0 0 18 26" fill="none" xmlns="http://www.w3.org/2000/svg"  style=${style} className=${className}>
    <path d="M9 0C4.03714 0 0 4.082 0 9.1C0 15.925 9 26 9 26C9 26 18 15.925 18 9.1C18 4.082 13.9629 0 9 0Z" fill="${fillBackground}"/>
    <g clip-path="url(#clip0_7630_43601)">
    <path d="M13.0833 4.75H4.91667C4.275 4.75 3.75583 5.275 3.75583 5.91667L3.75 14.0833C3.75 14.725 4.275 15.25 4.91667 15.25H13.0833C13.725 15.25 14.25 14.725 14.25 14.0833V5.91667C14.25 5.275 13.725 4.75 13.0833 4.75ZM12.5 11.1667H10.1667V13.5H7.83333V11.1667H5.5V8.83333H7.83333V6.5H10.1667V8.83333H12.5V11.1667Z" fill="${fill}"/>
    </g>
    <defs>
    <clipPath id="clip0_7630_43601">
    <rect width="14" height="14" fill="${fill}" transform="translate(2 3)"/>
    </clipPath>
    </defs>
  </svg>
`;
};





export const PlusWithSurroundingCircle = ({ className = "", fill = "white", fillBackground = "#FF7B42", style = {}, width = "1rem", height = "1rem" ,onClick=null }) => {
  return (
    <svg width={width} height={height} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={style} className={className} onClick={onClick}>
      <path fill-rule="evenodd" clip-rule="evenodd" d="M12 24C18.6274 24 24 18.6274 24 12C24 5.37258 18.6274 0 12 0C5.37258 0 0 5.37258 0 12C0 18.6274 5.37258 24 12 24ZM10.7368 10.7368V4H13.2632V10.7368H20V13.2632H13.2632V20H10.7368V13.2632H4V10.7368H10.7368Z" 
      fill={fill}/>
    </svg>
  );
};