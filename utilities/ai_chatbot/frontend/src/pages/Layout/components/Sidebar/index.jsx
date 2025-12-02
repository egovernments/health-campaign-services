import './style.scss';
import { Layout, Menu, Dropdown } from 'antd';
import { useLocation } from 'react-router-dom';
import AuthContext from '../../../../state/authContex';
import ml_icon from '../../../../assets/imgs/ml_icon.png';
import { Link } from 'react-router-dom/cjs/react-router-dom.min';
import React, { useState, useContext, useEffect } from 'react';
import { DatabaseOutlined, SettingOutlined, UserOutlined, TagOutlined, HistoryOutlined, LinkOutlined } from '@ant-design/icons';

const { Sider } = Layout;

const Sidebar = () => {
  const location = useLocation();
  const { roles } = useContext(AuthContext);

  const [selectedKey, setSelectedKey] = useState(location.pathname);

  useEffect(() => {
    if (roles === 'CHATBOT_ADMIN' || roles === 'admin') {
      setSelectedKey(location.pathname);
    } else {
      setSelectedKey('/');
    }
  }, [location.pathname, roles]);

  const handleMenuClick = (e) => {
    setSelectedKey(e.key);
  };

  function getItem(label, key, icon, children, type) {
    return {
      key,
      icon,
      children,
      label,
      type,
    };
  }

  const itemsSuperAdmin = [
    getItem(<Link to='/'>Tag Management</Link>, '/', <TagOutlined />),
    getItem(<Link to='/usermanagement'>User Management</Link>, '/usermanagement', <UserOutlined />),
    getItem(
      <Link to='/llmconfiguration'>LLM Configurations</Link>,
      '/llmconfiguration',
      <img
        src={ml_icon}
        alt='LLM Configurations Icon'
        style={{ width: 16, height: 16 }}
      />
    ),
    getItem(<Link to='/connections'>Datasource Connections</Link>, '/connections', <LinkOutlined />),
    getItem(<Link to='/botconfiguration'>Bot Configurations</Link>, '/botconfiguration', <DatabaseOutlined />),
    getItem(<Link to='/chathistory'>Chat History</Link>, '/chathistory', <HistoryOutlined />),
    getItem(<Link to='/settings'>Settings</Link>, '/settings', <SettingOutlined />),
  ];

  const itemsUser = [getItem(<Link to='/'>Bot Configurations</Link>, '/', <DatabaseOutlined />)];

  return (
    <Sider className='sider-style' collapsedWidth='50px' collapsed>
      <Menu className='menu-style' onClick={handleMenuClick} selectedKeys={[location.pathname]} theme='light' mode='inline' items={roles === 'CHATBOT_ADMIN' ? itemsSuperAdmin : itemsUser} />
    </Sider>
  );
};

export default Sidebar;
