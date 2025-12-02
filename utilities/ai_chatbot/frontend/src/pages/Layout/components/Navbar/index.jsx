import { Link } from 'react-router-dom';
import { Layout, Dropdown, Avatar, Space } from 'antd';
import AuthContext from '../../../../state/authContex';
import { useState, useEffect, useContext } from 'react';
import navBarLogo from '../../../../assets/imgs/logo.png';
import { getUISettings } from '../../../../services/ProductApis';
import { UserOutlined, LogoutOutlined, DownOutlined } from '@ant-design/icons';
import './style.scss';

const { Header } = Layout;

const Navbar = () => {
  const [logoUrl, setLogoUrl] = useState(navBarLogo);
  const [navbarBackground, setNavbarBackground] = useState('#2a71bc');
  const [loading, setLoading] = useState(true);

  const { token } = useContext(AuthContext);

  useEffect(() => {
    if (!token) {
      window.location.href = '/login';
      return;
    }
  }, [token]);

  useEffect(() => {
    const cachedUISettings = localStorage.getItem('uiSettings');

    if (cachedUISettings) {
      const settings = JSON.parse(cachedUISettings);
      setLogoUrl(settings.navbar_logo_url || navBarLogo);
      setNavbarBackground(settings.navbar_background_color || '#2a71bc');
      setLoading(false);
    } else {
      const fetchUISettings = async () => {
        try {
          const response = await getUISettings();
          const { value } = response.data;
          const settings = JSON.parse(value);

          setLogoUrl(settings.navbar_logo_url || navBarLogo);
          setNavbarBackground(settings.navbar_background_color || '#2a71bc');
          localStorage.setItem('uiSettings', JSON.stringify(settings));
        } catch (error) {
          console.error('Error fetching UI settings:', error);
        } finally {
          setLoading(false);
        }
      };

      fetchUISettings();
    }
  }, []);

  const logOut = () => {
    try {
      localStorage.removeItem('access_token');
      localStorage.removeItem('uiSettings');
      localStorage.removeItem('roles');
      localStorage.removeItem('guid');
      localStorage.removeItem('user_name');
      window.location.href = '/login';
    } catch (err) {
      console.error(err);
    }
  };

  const items = [
    {
      label: 'Logout',
      key: 'item-1',
      icon: <LogoutOutlined />,
      onClick: logOut,
    },
  ];

  if (loading) return null;

  return (
    <Header className='my-header' style={{ backgroundColor: navbarBackground }}>
      <div className='logo-container'>
        <Link to={'/'}>
          <img src={logoUrl} alt='navBarLogo' className='logo-image' />
        </Link>
      </div>

      <div className='profile'>
        <Avatar size='small' icon={<UserOutlined />} />
        <span className='username'>{localStorage.getItem('user_name') || 'User'}</span>
        <Dropdown className='dropdown' arrow menu={{ items }}>
          <a className='ant-dropdown-link' href='/' onClick={(e) => e.preventDefault()}>
            <Space>
              <DownOutlined />
            </Space>
          </a>
        </Dropdown>
      </div>
    </Header>
  );
};

export default Navbar;
