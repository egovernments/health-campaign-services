import { notification } from 'antd';

const notificationProps = {
  className: 'custom-class',
  style: { width: 350 },
};

export const showNotification = (icon, message, description) => {
  notification.open({ ...notificationProps, icon, message, description });
};
